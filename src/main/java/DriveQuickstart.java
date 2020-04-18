import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.services.drive.model.Permission;

import java.io.*;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

public class DriveQuickstart {
    private static final String APPLICATION_NAME = "Google Drive API Java Quickstart";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final String UPLOAD_FILE_PATH = "photos/big.JPG";
    private static final java.io.File UPLOAD_FILE = new java.io.File(UPLOAD_FILE_PATH);

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE_FILE);
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

    /**
     * Creates an authorized Credential object.
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        // Load client secrets.
        InputStream in = DriveQuickstart.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    public static void main(String... args) throws IOException, GeneralSecurityException {
        // Build a new authorized API client service.
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        Drive service = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();

        String fileName = uploadFile(service, "1", false);
        String queryParam = "name contains '"+ fileName + "'";
        // Print the names and IDs for up to 10 files.
        FileList result = service.files()
                .list()
                .setQ(queryParam)
                .setPageSize(10)
                .setFields("nextPageToken, files(id, name, webViewLink)")
                .execute();
        List<File> files = result.getFiles();
        if (files == null || files.isEmpty()) {
            System.out.println("No files found.");
        } else {
            System.out.println("Files:");
            for (File file : files) {
                System.out.printf("%s (%s) [%s]\n", file.getName(), file.getId(), file.getWebViewLink());
            }
        }
//        String folderId = createFolder(service);
//        if(folderId != null) {
//            System.out.println("Folder created successfully");
//            String fileId = uploadFile(service, folderId, false);
//            //downloadFile(service, fileId);
//        }
    }

    private static String createFolder(Drive driveService) {
        File fileMetadata = new File();
        fileMetadata.setName("Invoices");
        fileMetadata.setMimeType("application/vnd.google-apps.folder");
        try {
            File file = driveService.files().create(fileMetadata)
                    .setFields("id")
                    .execute();
            System.out.println("Folder ID: " + file.getId());
            return file.getId();
        }
        catch(Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String createFile(Drive driveService, String folderId) {
        File fileMetadata = new File();
        fileMetadata.setName("photo.png");
        fileMetadata.setParents(Collections.singletonList(folderId));
        java.io.File filePath = new java.io.File("photos/demoSign.png");
        FileContent mediaContent = new FileContent("image/png", filePath);
        try {
            File file = driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id, parents")
                    .execute();
            System.out.println("File ID: " + file.getId());
            return file.getId();
        }
        catch(Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String uploadFile(Drive drive, String folderId , boolean useDirectUpload) throws IOException {

        /*
        * drive: an instance of com.google.api.services.drive.Drive class
        * folderId: The id of the folder where you want to upload the file, It can be
        * located in 'My Drive' section or 'Shared with me' shared drive with proper
        * permissions.
        * useDirectUpload: Ensures whether using direct upload or Resume-able uploads.
        * */
        folderId = "<<shared folder id>>"; //
        File fileMetadata = new File();
        fileMetadata.setName(UPLOAD_FILE.getName());
        fileMetadata.setParents(Collections.singletonList(folderId));
        FileContent mediaContent = new FileContent("image/jpeg", UPLOAD_FILE);

        try {
            Drive.Files.Create create = drive.files().create(fileMetadata, mediaContent);
            MediaHttpUploader uploader = create.getMediaHttpUploader();
            //choose your chunk size and it will automatically divide parts
            uploader.setChunkSize(MediaHttpUploader.MINIMUM_CHUNK_SIZE);
            //according to Google, this enables gzip in future (optional)
            uploader.setDisableGZipContent(false);
            uploader.setDirectUploadEnabled(useDirectUpload);
            uploader.setProgressListener(new FileUploadProgressListener());
            File file =  create.execute();
            System.out.println("File ID: " + file.getId());
            System.out.println("File Name: " + file.getName());
            return file.getName();
        }
        catch(Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static boolean setPermission(Drive driveService, String fileId) {
        try {
            Permission userPermission = new Permission()
                    .setType("anyone")
                    .setRole("reader");
            driveService.permissions().create(fileId, userPermission)
                    .setFields("id");
            return true;
        }
        catch(Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private static boolean downloadFile(Drive driveService, String fileId) {
        try {
            OutputStream outputStream = new ByteArrayOutputStream();
            driveService.files().get(fileId)
                    .executeMediaAndDownloadTo(outputStream);
            OutputStream fileOutputStream = new FileOutputStream("C:\\Backup\\1.png");
            ((ByteArrayOutputStream) outputStream).writeTo(fileOutputStream);
            return true;
        }
        catch(Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}