package com.opencgl.dubbo.model;

import com.opencgl.base.model.BaseResponse;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author Chance.W
 */
public class DubboResponse extends BaseResponse {
    private Object object;

    public DubboResponse() {}

    public DubboResponse(Object object) {
        this.object = object;
    }

    public Object getObject() { return object; }
    public void setObject(Object object) { this.object = object; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Object object;

        public Builder object(Object object) { this.object = object; return this; }

        public DubboResponse build() {
            return new DubboResponse(object);
        }
    }
}
