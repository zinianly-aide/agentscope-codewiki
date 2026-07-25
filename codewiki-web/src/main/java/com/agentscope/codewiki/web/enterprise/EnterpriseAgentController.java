package com.agentscope.codewiki.web.enterprise;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** REST API for the minimal enterprise AgentScope-style single agent. */
@RestController
@RequestMapping("/api/agent")
public class EnterpriseAgentController {
    private final EnterpriseAgentService enterpriseAgentService;

    public EnterpriseAgentController(EnterpriseAgentService enterpriseAgentService) {
        this.enterpriseAgentService = enterpriseAgentService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        AgentChatResult result = enterpriseAgentService.chat(request);
        return new ChatResponse(
                request.requestId(), result.answer(), result.executionMode().wireValue());
    }

    /** Minimal error handling because this module did not have a global response wrapper. */
    @RestControllerAdvice(assignableTypes = EnterpriseAgentController.class)
    static class EnterpriseAgentExceptionHandler {
        @ExceptionHandler(MethodArgumentNotValidException.class)
        ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException ex) {
            String field =
                    ex.getBindingResult().getFieldErrors().stream()
                            .findFirst()
                            .map(FieldError::getField)
                            .orElse("request");
            return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", field));
        }

        @ExceptionHandler(EnterpriseAgentException.class)
        ResponseEntity<ErrorResponse> agent(EnterpriseAgentException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("AGENT_ERROR", ex.getMessage()));
        }
    }

    record ErrorResponse(String code, String message) {}
}
