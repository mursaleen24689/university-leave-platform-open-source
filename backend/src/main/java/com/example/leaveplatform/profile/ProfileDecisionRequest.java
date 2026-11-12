package com.example.leaveplatform.profile;

import jakarta.validation.constraints.Size;

/**
 * The office's sentence about its decision. Optional when approving - the
 * approval is the sentence - and required when refusing, because a refusal the
 * person cannot act on only produces the same request again.
 */
public record ProfileDecisionRequest(@Size(max = ProfileRequest.MAX_NOTE) String note) {
}
