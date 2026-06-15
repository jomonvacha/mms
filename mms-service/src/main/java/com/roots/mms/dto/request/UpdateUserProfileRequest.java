package com.roots.mms.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserProfileRequest {
    @Size(max = 50)
    @Email
    private String email;

    /** Optional public handle change; subject to format, uniqueness and cooldown. */
    @Size(min = 3, max = 20)
    @Pattern(regexp = "^[A-Za-z0-9_]+$",
            message = "Username may contain only letters, numbers, and underscores")
    private String username;

    @Size(max = 50)
    private String firstName;
    @Size(max = 50)
    private String lastName;
    @Size(max = 15)
    private String phoneNumber;


}
