package dev.xethh.tools.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document("uploaded")
public class FileUpload {
    @Id
    private String id;
    private String hash;
    private Long size;
    private String fileName;
    private String code;
}
