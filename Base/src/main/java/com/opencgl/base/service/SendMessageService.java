package com.opencgl.base.service;



import com.opencgl.base.model.BaseRequest;
import com.opencgl.base.model.BaseResponse;

/**
 * @author Chance.W
 */
public interface SendMessageService<T extends BaseRequest, R extends BaseResponse> {

    R send(T request) throws Exception;

}
