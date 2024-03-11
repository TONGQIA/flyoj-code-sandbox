package com.tong.flyojcodesandbox.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 判题信息
 *
 * @author tong
 * 
 */
@Data
public class JudgeInfo implements Serializable {

    /**
     * 程序执行信息
     */
    private String message;

    /**
     * 程序执行错误信息
     */
    private String errorMessage;

    /**
     * 消耗内存(KB)
     */
    private Long memory;

    /**
     * 消耗时间(ms)
     */
    private Long time;
}