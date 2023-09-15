package com.rainlu.api.manage.util;

import com.google.gson.Gson;
import com.rainlu.api.common.common.ErrorCode;
import com.rainlu.api.common.exception.BusinessException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * @description 使用反射，在获取到接口信息的情况下，动态适配SDK以调用接口，提高接口调用的灵活性
 *                  - interfaceInfo表中需要提供：SDK的全限定路径、调用接口的方法名
 */
public class InvokeUtil {


    // TODO：反射调用接口
    /**
     * @param classPath SDK中，访问接口的`客户端类`的全限定名称，被存在接口信息表中（每个一个接口提供一个SDK-client）。
     * @param methodName `客户端类`中提供具体接口调用服务的方法名
     * @param userRequestParams 访问接口的请求参数
     * @return java.lang.Object 接口的执行结果
     * @description 使用反射调用指定的接口
     */
    public static Object invokeInterfaceInfo(String classPath, String methodName, String userRequestParams,
                                       String accessKey, String secretKey) {
        try {
            Class<?> clientClazz = Class.forName(classPath);
            // 1. 获取`客户端类`构造器，参数为ak,sk
            Constructor<?> binApiClientConstructor = clientClazz.getConstructor(String.class, String.class);
            // 2. 构造出客户端：new XXXClient
            Object apiClient = binApiClientConstructor.newInstance(accessKey, secretKey);

            // 3. 找到要调用的方法：XXXClient中的方法
            Method[] methods = clientClazz.getMethods();
            for (Method method : methods) {
                if (method.getName().equals(methodName)) {
                    // 3.1 获取参数类型列表
                    Class<?>[] parameterTypes = method.getParameterTypes();
                    if (parameterTypes.length == 0) {
                        // 如果没有参数，直接调用
                        return method.invoke(apiClient);
                    }
                    Gson gson = new Gson();
                    // 构造参数
                    Object parameter = gson.fromJson(userRequestParams, parameterTypes[0]);
                    return method.invoke(apiClient, parameter);
                }
            }
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "找不到调用的方法!! 请检查你的请求参数是否正确!");
        }
    }

}
