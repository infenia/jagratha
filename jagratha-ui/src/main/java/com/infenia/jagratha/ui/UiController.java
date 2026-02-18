package com.infenia.jagratha.ui;

import com.infenia.jagratha.config.AppConfigService;
import com.infenia.jagratha.service.AppService;
import com.infenia.jagratha.service.TaskTrackerService;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Controller for the Jagratha UI. */
@Controller
@RequestMapping("/ui")
@RequiredArgsConstructor
public class UiController {

  private final AppService appService;
  private final AppConfigService configService;
  private final TaskTrackerService taskTrackerService;
  private final TemplateEngine templateEngine;

  @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  public Mono<String> index(Model model) {
    model.addAttribute("sessions", appService.getAllSessions());
    return Mono.fromCallable(
            () -> {
              final StringOutput output = new StringOutput();
              templateEngine.render("index.jte", model.asMap(), output);
              return output.toString();
            })
        .subscribeOn(Schedulers.boundedElastic());
  }

  @GetMapping(value = "/sessions/{sessionId}", produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  public Mono<String> session(@PathVariable String sessionId, Model model) {
    model.addAttribute("sessionId", sessionId);
    model.addAttribute("config", configService.getAllConfigs(sessionId));
    model.addAttribute("workflows", configService.getWorkflows(sessionId));
    model.addAttribute("modifiedFiles", appService.getModifiedFiles(sessionId));
    model.addAttribute("progress", taskTrackerService.getProgress(sessionId));

    return appService
        .listLogs(sessionId)
        .map(
            logs -> {
              model.addAttribute("logs", logs);
              final StringOutput output = new StringOutput();
              templateEngine.render("session.jte", model.asMap(), output);
              return output.toString();
            })
        .subscribeOn(Schedulers.boundedElastic());
  }

    @GetMapping(value = "/api/sessions/{sessionId}/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public Flux<String> streamLogs(@PathVariable String sessionId) {
        return taskTrackerService.getLogStream(sessionId);
    }

    @GetMapping(value = "/api/sessions/{sessionId}/status/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public Flux<String> streamStatus(@PathVariable String sessionId) {
        return taskTrackerService.getStatusStream(sessionId);
    }
}
