package com.example.live.common;

import java.util.List;

public record PageResult<T>(List<T> list, long total, long page, long pageSize) {
}
