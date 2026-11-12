package com.example.leaveplatform.notification;

import java.util.List;

/**
 * The messages the person has just looked at.
 *
 * <p>{@code kind} is optional. With it, only the listed messages of that kind
 * are marked, which is what a screen that shows its own rows should send; without
 * it every listed message of the caller's own is marked, which is what the bell
 * box does.
 */
public record MarkReadRequest(List<Long> ids, String kind) {
}
