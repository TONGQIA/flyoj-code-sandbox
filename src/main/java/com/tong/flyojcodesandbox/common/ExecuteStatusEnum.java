package com.tong.flyojcodesandbox.common;

/**
 * 自定义错误码
 *
 * @author tong
 * 
 */
public enum ExecuteStatusEnum {

    SUCCESS(0, "运行成功"),
    PARAMS_ERROR(1, "请求参数错误"),
    SYSTEM_ERROR(2, "系统内部异常"),
    RUN_ERROR(3, "程序运行异常"),
    COMPILE_ERROR(4, "编译错误"),
    NO_AUTH_ERROR(5, "无权限");


    /**
     * 状态码
     */
    private final int code;

    /**
     * 信息
     */
    private final String message;

    ExecuteStatusEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

}
