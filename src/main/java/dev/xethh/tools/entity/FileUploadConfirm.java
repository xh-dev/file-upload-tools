package dev.xethh.tools.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document("confirmed")
public class FileUploadConfirm {
    @Id
    private String id;
    private String hash;
    private Long size;
    private String fileName;
    private String code;
    private String originalId;
}
