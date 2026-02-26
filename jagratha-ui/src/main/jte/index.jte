@import java.util.List
@import java.util.Map
@param List<Map<String, Object>> sessions

@template.layout.main(title = "Dashboard", content = @`
    <div class="mb-6">
        <h1 class="text-4xl font-black mb-2 text-slate-800 dark:text-slate-50">Active Sessions</h1>
        <p class="text-slate-500 dark:text-slate-300 text-lg max-w-2xl leading-relaxed">
            Manage and monitor your current engineering sessions and AI agent workflows.
        </p>
    </div>

    <div class="mb-6 max-w-xl">
        <div class="relative">
            <span class="material-symbols-outlined absolute left-5 top-1/2 -translate-y-1/2 text-slate-400">search</span>
            <input id="search-input" type="text"
                   class="w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-3xl py-4 pl-14 pr-6 focus:outline-none focus:border-primary transition-all"
                   placeholder="Search sessions by ID, name, or initiator...">
        </div>
    </div>

    @if(sessions == null || sessions.isEmpty())
        <div class="text-center py-24 bg-white/30 dark:bg-slate-800/20 rounded-3xl border border-dashed border-slate-200 dark:border-slate-700 backdrop-blur-sm">
            <div class="w-20 h-20 mx-auto bg-slate-100 dark:bg-slate-800 rounded-full flex items-center justify-center text-slate-400 dark:text-slate-600 mb-6">
                <span class="material-symbols-outlined text-4xl">sensors_off</span>
            </div>
            <p class="text-slate-500 dark:text-slate-400 max-w-sm mx-auto">
                No active sessions found.
            </p>
        </div>
    @else
        <div id="session-grid" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
            @for(Map<String, Object> session : sessions)
                <div class="group p-5 relative bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-2xl overflow-hidden shadow-sm hover:shadow-xl transition-all duration-300 flex flex-col min-h-64 border-l-4 border-l-primary cursor-pointer"
                     onclick="window.location='/ui/sessions/${(String)session.get("sessionId")}'">
                    <div class="mb-4">
                        <span class="inline-flex items-center px-3.5 py-1.5 text-xs font-mono font-semibold bg-primary/10 text-primary truncate rounded-xl border border-primary/10 dark:border-primary/10">
                            #${(String)session.get("sessionId")}
                        </span>
                    </div>

                    <h3 class="text-lg font-semibold leading-tight text-slate-900 dark:text-slate-100 line-clamp-2 mb-5 min-h-2">
                        ${(String)session.getOrDefault("description", "No description provided.")}
                    </h3>

                    <div class="flex flex-wrap gap-2 mb-5">
                        @if(session.get("workflows") instanceof Map workflows)
                            @for(Object workflowId : workflows.keySet())
                                <span class="inline-flex items-center px-3 py-1 text-xs font-medium bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 rounded-full border border-slate-200 dark:border-slate-700">
                                    ${String.valueOf(workflowId)}
                                </span>
                            @endfor
                        @endif
                    </div>

                    <div class="mt-auto pt-4 border-t border-slate-100 dark:border-slate-800 text-sm text-slate-600 dark:text-slate-400 flex justify-between items-center">
                        <div class="flex items-center gap-1.5">
                            <span class="text-slate-500 dark:text-slate-400">Initiator:</span>
                            <span class="font-medium">${(String)session.getOrDefault("initiator", "System")}</span>
                        </div>
                        <div class="text-right">
                            <span class="text-slate-500 dark:text-slate-400">Initiated At</span>
                            <span class="font-medium">
                                !{
                                    String initiatedTime = (String) session.get("initiatedTime");
                                    String formattedTime = "Unknown";
                                    if (initiatedTime != null && !initiatedTime.isEmpty()) {
                                        try {
                                            java.time.Instant initiatedAt = java.time.Instant.parse(initiatedTime);
                                            java.time.ZonedDateTime zdt = initiatedAt.atZone(java.time.ZoneId.systemDefault());
                                            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a");
                                            formattedTime = zdt.format(formatter);
                                        } catch (Exception e) {
                                            formattedTime = "Invalid date";
                                        }
                                    }
                                }
                            ${formattedTime}</span>
                        </div>
                    </div>
                </div>
            @endfor
        </div>
    @endif

    <script>
        document.getElementById('search-input').addEventListener('input', e => {
            const term = e.target.value.toLowerCase();
            document.querySelectorAll('#session-grid > div').forEach(card => {
                card.style.display = card.textContent.toLowerCase().includes(term) ? '' : 'none';
            });
        });
    </script>
`)
