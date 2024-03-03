package com.xtool.opencgl.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DialogStyleDto {
    private String type;
    private String text;
    private Long level;
}
