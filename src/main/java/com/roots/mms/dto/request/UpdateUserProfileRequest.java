package com.roots.mms.dto.request;

import jakarta.validation.constraints.Size;
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
  private String firstName;
  @Size(max = 50)
  private String lastName;
  @Size(max = 15)
  private String phoneNumber;


}
