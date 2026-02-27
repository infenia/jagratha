@import java.util.Map
@import java.util.List
@import com.infenia.jagratha.model.WorkflowDefinition
@import com.infenia.jagratha.model.PluginDetails
@import com.infenia.jagratha.model.WorkflowProgress

@param String sessionId
@param String workflowId
@param WorkflowDefinition workflow
@param Map<String, PluginDetails> pluginDetails
@param WorkflowProgress progress
@param List<String> logs

@template.layout.main(title = "Workflow " + workflowId, content = @`
    <!-- Breadcrumbs -->
    <div class="flex flex-wrap gap-2 py-4">
        <a href="/ui" class="text-slate-500 dark:text-slate-400 text-sm font-medium hover:text-primary transition-colors">Workflows</a>
        <span class="text-slate-300 dark:text-slate-700 text-sm">/</span>
        <span class="text-slate-900 dark:text-white font-semibold text-sm">${workflowId}</span>
    </div>

    <!-- Workflow Title -->
    <div class="flex flex-wrap justify-between items-end gap-3 pb-6">
        <div class="flex flex-col gap-1">
            <h1 class="text-3xl font-bold leading-tight text-slate-900 dark:text-white">Workflow Details</h1>
            <p class="text-sm font-mono uppercase tracking-wider text-slate-500 dark:text-slate-400">ID: ${workflowId}</p>
        </div>
    </div>

    <!-- DAG Visualization Area -->
    <div class="relative w-full aspect-[21/9] bg-white dark:bg-slate-900/40 rounded-xl border border-slate-200 dark:border-slate-800/60 backdrop-blur-sm overflow-hidden shadow-xl flex items-center justify-center p-8 mb-6">
        <!-- SVG DAG Lines -->
        <svg class="absolute inset-0 w-full h-full pointer-events-none">
            !{
                java.util.List<WorkflowDefinition.Edge> edges = workflow.edges();
                java.util.List<WorkflowDefinition.Node> nodes = workflow.nodes();
            }
            @for(WorkflowDefinition.Edge edge : edges)
                <!-- Connection lines between nodes would be drawn here -->
            @endfor
        </svg>

        <div class="flex items-center justify-between w-full relative z-10 px-12 flex-wrap gap-8">
            @if(workflow.nodes() != null && !workflow.nodes().isEmpty())
                @for(WorkflowDefinition.Node node : workflow.nodes())
                    !{
                        String nodeType = node.type();
                        String nodeIcon = "settings";
                        String nodeColor = "text-slate-500";
                        if ("trigger".equalsIgnoreCase(nodeType)) {
                            nodeIcon = "input";
                            nodeColor = "text-primary";
                        } else if ("processor".equalsIgnoreCase(nodeType)) {
                            nodeIcon = "settings";
                            nodeColor = "text-emerald-500";
                        } else if ("terminal".equalsIgnoreCase(nodeType)) {
                            nodeIcon = "output";
                            nodeColor = "text-blue-500";
                        }
                    }
                    <div class="dag-node p-4 rounded-xl shadow-sm border border-slate-200/50 dark:border-slate-700/50 bg-white/40 dark:bg-slate-800/40 backdrop-blur-md flex flex-col gap-2 min-w-[160px]">
                        <div class="flex items-center gap-2">
                            <span class="material-symbols-outlined ${nodeColor} text-lg">${nodeIcon}</span>
                            <span class="font-bold text-xs uppercase text-slate-500 dark:text-slate-400 tracking-widest">${nodeType}</span>
                        </div>
                        <p class="text-sm font-semibold text-slate-900 dark:text-white">${node.nodeId()}</p>
                    </div>
                @endfor
            @endif
        </div>

        <!-- Controls Overlay -->
        <div class="absolute bottom-4 right-4 flex gap-2">
            <button class="bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 p-2 rounded-lg text-slate-400 hover:text-slate-600 dark:hover:text-white shadow-sm transition-colors">
                <span class="material-symbols-outlined">zoom_in</span>
            </button>
            <button class="bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 p-2 rounded-lg text-slate-400 hover:text-slate-600 dark:hover:text-white shadow-sm transition-colors">
                <span class="material-symbols-outlined">zoom_out</span>
            </button>
            <button class="bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 p-2 rounded-lg text-slate-400 hover:text-slate-600 dark:hover:text-white shadow-sm transition-colors">
                <span class="material-symbols-outlined">center_focus_strong</span>
            </button>
        </div>
    </div>

    <!-- Terminal Log Viewer -->
    <div class="log-container rounded-xl overflow-hidden mb-8 shadow-2xl flex flex-col min-h-[320px] border border-slate-800/60">
        <div class="terminal-header px-6 py-3 flex items-center justify-between border-b border-slate-800">
            <div class="flex items-center gap-2">
                <span class="material-symbols-outlined text-slate-400 text-sm">terminal</span>
                <span class="text-xs font-mono font-bold uppercase tracking-wider text-slate-300">Live Logs</span>
            </div>
            <div class="flex gap-4">
                <button class="text-xs font-mono text-slate-400 hover:text-white flex items-center gap-1 transition-colors">
                    <span class="material-symbols-outlined text-xs">download</span> Download
                </button>
                <button class="text-xs font-mono text-slate-400 hover:text-white flex items-center gap-1 transition-colors" onclick="document.getElementById('log-content').innerHTML = ''">
                    <span class="material-symbols-outlined text-xs">close</span> Clear
                </button>
            </div>
        </div>

        <div id="log-content" class="p-4 font-mono text-xs leading-relaxed overflow-y-auto max-h-[300px] text-slate-300">
            @if(logs != null && !logs.isEmpty())
                @for(String log : logs)
                    <p class="text-slate-300">${log}</p>
                @endfor
            @else
                <p class="text-slate-600 animate-pulse">➜ Listening for workflow logs...</p>
            @endif
        </div>
    </div>

    <style>
        .dag-node {
            transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
        }
        .dag-node:hover {
            transform: translateY(-2px);
            box-shadow: 0 20px 25px -5px rgb(0 0 0 / 0.1), 0 8px 10px -6px rgb(0 0 0 / 0.1);
        }

        .dark .dag-node {
            box-shadow: 0 10px 15px -3px rgb(0 0 0 / 0.3);
        }

        .dag-line {
            stroke: #94a3b8;
            stroke-width: 1.5;
            opacity: 0.4;
        }

        .terminal-header {
            background: #111827;
        }

        .log-container {
            background: #0a0f18;
            border-color: #1e2937;
        }
    </style>
`)
