package dev.xethh.tools.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@Document("uploaded")
@CompoundIndexes(
        @CompoundIndex(name = "user-scope-and-code", def = "{'userScope' : 1, 'path': 1, 'version': 1}", unique = true)
)
public class FileUpload {
    @Id
    private String id;
    private String userScope;
    private String path;
    private String sha2;
    private String sha3;
    private Long revision;
    private Long size;
    private String refFile;
}
