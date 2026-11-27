<template>
  <header v-if="page" class="workspace-page-heading">
    <p>{{ page.category }}</p><h1>{{ page.title }}</h1><span>{{ page.description }}</span>
  </header>
</template>
<script setup>
import { workspacePage } from '@/utils/workspacePages'
const route = useRoute()
const page = computed(() => workspacePage(route))
</script>
<style scoped>
.workspace-page-heading { padding: 1.75rem 1.75rem .35rem; }
.workspace-page-heading p { margin: 0 0 .55rem; color: var(--lx-primary); font-size: .75rem; font-weight: 650; }
.workspace-page-heading h1 { margin: 0; font: 650 1.65rem/1.3 "Segoe UI Variable Display", "Microsoft YaHei UI", sans-serif; letter-spacing: -.035em; }
.workspace-page-heading span { display: block; color: var(--lx-muted); font-size: .8rem; line-height: 1.7; margin-top: .65rem; }
@media(max-width: 768px) { .workspace-page-heading { padding: 1.25rem 1rem .25rem; } }
</style>
