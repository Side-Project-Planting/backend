package com.example.planservice.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Getter
public class TabCreateRequest {
    @NotBlank
    private String name;

    @NotNull
    private Long planId;

    @Builder
    private TabCreateRequest(String name, Long planId) {
        this.name = name;
        this.planId = planId;
    }
}
