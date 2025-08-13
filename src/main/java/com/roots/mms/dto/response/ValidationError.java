package com.roots.mms.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationError {

    @JsonProperty("field")
    private String field;

    @JsonProperty("rejected_value")
    private Object rejectedValue;

    @JsonProperty("message")
    private String message;

    @JsonProperty("code")
    private String code;
}
