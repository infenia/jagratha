package io.jagratha.jagratha.model;

import lombok.Data;

@Data
public class FileRequest {
    private String path;
    private String content;
}
