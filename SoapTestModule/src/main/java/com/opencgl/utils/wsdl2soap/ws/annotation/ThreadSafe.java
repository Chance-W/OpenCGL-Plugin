package com.opencgl.utils.wsdl2soap.ws.annotation;

import java.lang.annotation.*;


@Documented
@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.CLASS)
public @interface ThreadSafe {
}
