import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class FileUtils {
  public static final String FILE_PART_FORMAT_STRING = "part_%d_of_%d";
  public static final String FILE_PART_DIR_SUFFIX = "_parts";
  public static final String separator = File.separator;

  public static boolean saveFile(String filePath, byte[] fileContentBytes,
                                 boolean append) {
    Path path = Paths.get(filePath);
    Path parentDir = path.getParent();

    // Append the part to the file
    try (FileOutputStream fos = new FileOutputStream(filePath, append)) {
      // Ensure the parent directories exist
      if (parentDir != null) {
        Files.createDirectories(parentDir);
      }
      fos.write(fileContentBytes);
    } catch (IOException e) {
      return false;
    }
    return true;
  }

  public static boolean
  assembleFileAndCleanup(File partFileDir, Path destinationPath, int totalParts)
      throws IOException {
    assert partFileDir.isDirectory();
    Path firstPartPath =
        Paths.get(partFileDir.toString(),
                  String.format(FILE_PART_FORMAT_STRING, 1, totalParts));

    for (int i = 2; i <= totalParts; i++) {
      Path nextPartPath =
          Paths.get(partFileDir.toString(),
                    String.format(FILE_PART_FORMAT_STRING, i, totalParts));
      appendFile(firstPartPath, nextPartPath);
    }

    Files.move(firstPartPath, destinationPath,
               StandardCopyOption.REPLACE_EXISTING);

    for (int i = 2; i <= totalParts; i++) {
      File nextPartFile =
          new File(partFileDir.toString(),
                   String.format(FILE_PART_FORMAT_STRING, i, totalParts));
      nextPartFile.delete();
    }
    boolean ok = partFileDir.delete();
    return ok;
  }

  public static void appendStreamedFiles(File destinationFile,
                                         File... sourceFiles)
      throws IOException {
    try (FileChannel destChannel =
             new FileOutputStream(destinationFile, true).getChannel()) {
      for (File sourceFile : sourceFiles) {
        try (FileChannel sourceChannel =
                 new FileInputStream(sourceFile).getChannel()) {
          long position = 0;
          long size = sourceChannel.size();
          while (position < size) {
            position += sourceChannel.transferTo(position, size - position,
                                                 destChannel);
          }
        }
      }
    }
  }

  public static void appendFiles(File destinationFile, File... sourceFiles)
      throws IOException {
    try (FileChannel destChannel =
             new FileOutputStream(destinationFile, true).getChannel()) {
      for (File sourceFile : sourceFiles) {
        try (FileChannel sourceChannel =
                 new FileInputStream(sourceFile).getChannel()) {
          long count = sourceChannel.size();
          sourceChannel.transferTo(0, count, destChannel);
        }
      }
    }
  }

  public static void appendFile(Path destination, Path source)
      throws IOException {
    try (FileChannel sourceChannel =
             FileChannel.open(source, StandardOpenOption.READ);
         FileChannel destinationChannel =
             FileChannel.open(destination, StandardOpenOption.WRITE,
                              StandardOpenOption.APPEND)) {
      sourceChannel.transferTo(0, sourceChannel.size(), destinationChannel);
    }
  }

  public static boolean allPartsReceived(File partFileDir, int totalParts) {
    for (int i = 1; i <= totalParts; i++) {
      File partFile =
          new File(partFileDir, String.format(FileUtils.FILE_PART_FORMAT_STRING,
                                              i, totalParts));
      if (!partFile.exists()) {
        return false;
      }
    }
    return true;
  }
}
