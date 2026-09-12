package ao.autocare.storage;

import org.springframework.stereotype.Component;

/** Constrói o URL público assinado de um ficheiro. */
@Component
public class FileUrls {

    private final StorageProperties props;
    private final FileUrlSigner signer;

    public FileUrls(StorageProperties props, FileUrlSigner signer) {
        this.props = props;
        this.signer = signer;
    }

    public String signed(String fileId) {
        if (fileId == null) return null;
        return props.publicBaseUrlOrDefault() + "/api/v1/files/" + fileId + "?sig=" + signer.sign(fileId);
    }
}
