package com.tong.flyojcodesandbox.model;

import lombok.Data;

import java.util.List;

/**
 * 进程执行信息
 */
@Data
public class ExecuteMassage {
    // 错误码
    private Integer code;

    // 基本信息
    private String message;

    // 错误信息
    private String errorMessage;

    // 消耗时间
    private long time;

    // 内存占用
    private long memory;
}
