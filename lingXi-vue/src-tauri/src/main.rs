#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod services;
use tauri::Manager;

fn main() {
    let app = tauri::Builder::default()
        .plugin(tauri_plugin_single_instance::init(|app, _, _| {
            if let Some(window) = app.get_webview_window("main") {
                let _ = window.unminimize();
                let _ = window.set_focus();
            }
        }))
        .plugin(tauri_plugin_updater::Builder::new().build())
        .setup(|app| {
            app.manage(services::LocalServices::start(app)?);
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![services::desktop_status, services::stop_desktop_services])
        .build(tauri::generate_context!())
        .expect("error while running LingXi Terminal");
    app.run(|app, event| {
        if let tauri::RunEvent::Exit = event {
            let _ = app.state::<services::LocalServices>().stop();
        }
    });
}
