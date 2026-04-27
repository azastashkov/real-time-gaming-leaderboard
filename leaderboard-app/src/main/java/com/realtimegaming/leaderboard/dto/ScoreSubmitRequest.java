package com.realtimegaming.leaderboard.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ScoreSubmitRequest(@NotNull @Min(0) Long score) {}
