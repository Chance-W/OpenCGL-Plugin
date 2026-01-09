package com.opencgl.dubbossl.model;

/**
 * Dubbo SSL 响应模型
 *
 * @author Chance.W
 */
public class DubboSslResponse {
    private Object object;
    
    private DubboSslResponse(Builder builder) {
        this.object = builder.object;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public Object getObject() {
        return object;
    }
    
    public static class Builder {
        private Object object;
        
        public Builder object(Object val) { 
            object = val; 
            return this; 
        }
        
        public DubboSslResponse build() {
            return new DubboSslResponse(this);
        }
    }
}
