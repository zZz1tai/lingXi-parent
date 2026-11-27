use std::{fs, io::Write, path::PathBuf, process::{Child, Command, Stdio}, sync::Mutex, time::{Duration, Instant}};
use tauri::Manager;

pub struct LocalServices {
    child: Mutex<Option<Child>>,
    status: PathBuf,
}

impl LocalServices {
    pub fn start(app: &tauri::App) -> Result<Self, Box<dyn std::error::Error>> {
        let data = app.path().app_local_data_dir()?.join("server");
        fs::create_dir_all(&data)?;
        let status = data.join("status.json");
        let runtime = app.path().resource_dir()?.join("runtime");
        // 开发模式仍使用根 start.ps1；发布包缺少资源必须明确报错。
        if cfg!(debug_assertions) && !runtime.join("launcher.py").exists() {
            fs::write(&status, r#"{"phase":"ready","message":"使用开发环境服务"}"#)?;
            return Ok(Self { child: Mutex::new(None), status });
        }
        fs::write(&status, r#"{"phase":"starting","message":"正在准备本机服务…"}"#)?;
        let log = fs::OpenOptions::new().create(true).append(true).open(data.join("launcher.log"))?;
        let mut command = Command::new(runtime.join("python/python.exe"));
        command.arg(runtime.join("launcher.py")).arg("--runtime").arg(&runtime)
            .arg("--data").arg(&data).current_dir(&data).stdin(Stdio::piped())
            .stdout(Stdio::from(log.try_clone()?)).stderr(Stdio::from(log));
        #[cfg(windows)]
        {
            use std::os::windows::process::CommandExt;
            command.creation_flags(0x08000000);
        }
        match command.spawn() {
            Ok(child) => Ok(Self { child: Mutex::new(Some(child)), status }),
            Err(_) => {
                fs::write(&status, r#"{"phase":"error","message":"安装包中的运行环境缺失或无法启动，请重新安装完整桌面版。"}"#)?;
                Ok(Self { child: Mutex::new(None), status })
            }
        }
    }

    pub fn stop(&self) -> Result<(), String> {
        if let Ok(mut slot) = self.child.lock() {
            if let Some(mut child) = slot.take() {
                // 通过管道通知管理器正常关闭数据库，升级前也执行同一逻辑。
                if let Some(mut pipe) = child.stdin.take() {
                    let _ = pipe.write_all(b"stop\n");
                }
                let deadline = Instant::now() + Duration::from_secs(55);
                while Instant::now() < deadline {
                    if !matches!(child.try_wait(), Ok(None)) { return Ok(()); }
                    std::thread::sleep(Duration::from_millis(100));
                }
                // 不强杀管理器，父管道关闭后仍允许它完成数据库刷盘。
                return Err("本机服务仍在关闭，请稍后重新打开应用再更新。".to_string());
            }
        }
        Ok(())
    }

    fn status(&self) -> serde_json::Value {
        let value = fs::read_to_string(&self.status).ok()
            .and_then(|text| serde_json::from_str::<serde_json::Value>(&text).ok());
        if let Ok(mut slot) = self.child.try_lock() {
            if let Some(child) = slot.as_mut() {
                if matches!(child.try_wait(), Ok(Some(_))) && value.as_ref().and_then(|v| v["phase"].as_str()) != Some("error") {
                    return serde_json::json!({"phase": "error", "message": "本机服务已退出，请重新打开应用并检查日志。"});
                }
            }
        }
        value.unwrap_or_else(|| serde_json::json!({"phase": "starting", "message": "正在准备本机服务…"}))
    }
}

#[tauri::command]
pub fn desktop_status(state: tauri::State<'_, LocalServices>) -> serde_json::Value {
    state.status()
}

#[tauri::command]
pub async fn stop_desktop_services(app: tauri::AppHandle) -> Result<(), String> {
    tauri::async_runtime::spawn_blocking(move || app.state::<LocalServices>().stop())
        .await.map_err(|_| "关闭本机服务失败".to_string())?
}
