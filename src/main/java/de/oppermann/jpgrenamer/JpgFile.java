package de.oppermann.jpgrenamer;

import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.apache.commons.imaging.ImageInfo;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.tiff.TiffField;
import org.apache.commons.imaging.formats.tiff.constants.TiffDirectoryType;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoAscii;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * This class maintains information about a JPG image file.
 * It contains the methods to load data from the image metadata
 * and to rename the file in the filesystem
 */
public class JpgFile {

    //region fields
    private File imageFile;

    private File renamedImageFile;

    private final BufferedImage thumbnail;

    private final int width;

    private final int height;

    private final Date taken;

    private final ReadOnlyStringWrapper currentName;

    private final StringProperty newName;

    private static final String DATE_PATTERN = "yyyy-MM-dd HH-mm-ss";

    protected static final String[] FILE_EXTENSIONS = {".jpg", ".jpeg" };

    //endregion

    /**
     * Creates a new JpgFile and populates the fields by loading the image file's metadata
     * @param imageFile the file in the filesystem
     * @throws IOException if the file does not exist or cannot be opened
     * @throws ImageReadException if the image cannot be loaded from the file
     * @throws ParseException if the data in the file does not match expectations
     */
    public JpgFile(File imageFile) throws IOException, ImageReadException, ParseException {

        //initialize properties
        this.currentName = new ReadOnlyStringWrapper(this, "currentName");
        this.newName = new SimpleStringProperty(this, "newName");
        this.newName.addListener((observableValue, oldValue, newValue) -> {
            if(newValue == null || newValue.equals(oldValue)) {
                return;
            }
            setNewName(newValue);
        });

        this.setImageFile(imageFile);
        JpegImageMetadata metadata = readMetaData();

        if(metadata != null) {
            //read metadata successfully
            this.taken = readDateTag(metadata);
            this.width = readWidth(metadata);
            this.height = readHeight(metadata);
            this.thumbnail = getThumbnail(metadata);
        } else {
            //failed to read metadata -> try using imageinfo
            ImageInfo info = this.readImageInfo();
            this.taken = readFileDate();
            this.width = readWidth(info);
            this.height = readHeight(info);
            this.thumbnail = getThumbnail(info);
        }

        //calculate suggested name from date taken
        SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_PATTERN);
        String suggestedName = dateFormat.format(taken);
        this.newName.setValue(suggestedName);
    }

    //region getters and setters

    /**
     * Returns the date the image was taken
     * @return the date the image was taken
     */
    public Date getTaken() {
        return taken;
    }

    /**
     * Returns the StringProperty for the new name.
     * The UI components can bind to this StringProperty
     * @return the StringProperty
     */
    public StringProperty newNameProperty() {
        return newName;
    }

    /**
     * Returns the resolution of the image
     * @return String representation of the resolution (width x height)
     */
    public String getResolution() {
        if(this.height < 0 || this.width < 0) {
            return "n/a";
        }
        return String.format("%d x %d", this.width, this.height);
    }

    /**
     * Returns the current name of the file
     * @return The current name of the file
     */
    public String getCurrentName() {
        return this.currentName.getValue();
    }

    /**
     * Returns the read only StringProperty containing the current name of the file.
     * UI components can bind to the property to be notified of changes (e.g. after rename has been executed)
     * @return the read-only String property
     */
    public ReadOnlyStringProperty currentNameProperty() {
        return this.currentName.getReadOnlyProperty();
    }

    /**
     * Sets the current name
     * @param name the current name
     */
    private void setCurrentName(String name) {
        this.currentName.setValue(name);
    }

    /**
     * Sets the image file
     * @param imageFile the image file
     */
    private void setImageFile(File imageFile) {
        this.imageFile = imageFile;
        this.setCurrentName(imageFile.getName());
    }

    /**
     * Sets the new name
     * @param newName the new name to set
     */
    private void setNewName(String newName) {
        if(!newName.endsWith(".jpg")) {
            newName += ".jpg";
            this.newName.setValue(newName);
            return;
        }
        renamedImageFile = new File(imageFile.getParentFile(), this.newName.getValue());
    }

    /**
     * Returns the thumbnail image of the image file
     * @return he thumbnail
     */
    public BufferedImage getThumbnail() {
        return thumbnail;
    }

    //endregion

    //region metadata

    /**
     * Reads the Jpeg meta data from the image file
     * @return the JpgMetadata, or null, if no metadata is contained
     * @throws IOException if the file cannot be read or accessed
     * @throws ImageReadException if the image file cannot be read
     */
    private JpegImageMetadata readMetaData() throws IOException, ImageReadException {
        final ImageMetadata metadata = Imaging.getMetadata(imageFile);

        if (metadata instanceof JpegImageMetadata) {
            return (JpegImageMetadata) metadata;
        } else {
            return null;
        }
    }

    /**
     * Returns the image information
     * @return The image information
     * @throws IOException if the file cannot be read or accessed
     * @throws ImageReadException if the image file cannot be read
     */
    private ImageInfo readImageInfo() throws IOException, ImageReadException {
        return Imaging.getImageInfo(this.imageFile);
    }

    /**
     * Reads the date from the Jpeg metadata. If the "DateTimeOriginal" tag is present,
     * it is used. Otherwise, the DateTime tag is used.
     * @param metadata The image metadata
     * @return The date parsed from the tag information
     * @throws ImageReadException if the tag cannot be read
     * @throws ParseException if the date format does not match the tag content
     */
    private Date readDateTag(JpegImageMetadata metadata) throws ImageReadException, ParseException {
        final String pattern = "yyyy:MM:dd HH:mm:ss";
        try {
            //try reading the original date tag
            TiffField field = metadata.findEXIFValue(
                    new TagInfoAscii("DateTimeOriginal", 36867, 20, TiffDirectoryType.EXIF_DIRECTORY_EXIF_IFD));
            SimpleDateFormat dateFormat = new SimpleDateFormat(pattern);
            return dateFormat.parse(field.getStringValue());
        } catch (ImageReadException | ParseException e) {
            TiffField field = metadata.findEXIFValue(TiffTagConstants.TIFF_TAG_DATE_TIME);
            SimpleDateFormat dateFormat = new SimpleDateFormat(pattern);
            return dateFormat.parse(field.getStringValue());
        }
    }

    /**
     * Reads the date from the file attributes. This method is used as a fallback if no metadata
     * can be read from the file.
     * @return The file creation date
     * @throws IOException if the file cannot be accessed
     */
    private Date readFileDate() throws IOException {
        var fileAttributes = Files.readAttributes(imageFile.toPath(), BasicFileAttributes.class);
        FileTime time = fileAttributes.creationTime();
        return new Date(time.toMillis());
    }

    /**
     * Reads the height of the image from the metadata
     * @param metadata The image metadata
     * @return The height (pixels)
     * @throws ImageReadException if the tag cannot be read
     */
    private int readHeight(JpegImageMetadata metadata) throws ImageReadException {
        TiffField field = metadata.findEXIFValue(TiffTagConstants.TIFF_TAG_IMAGE_LENGTH);
        return field.getIntValue();
    }

    /**
     * Reads the height from the image information
     * @param imageInfo The image information
     * @return The height of the image (pixels)
     */
    private int readHeight(ImageInfo imageInfo) {
        return imageInfo.getHeight();
    }

    /**
     * Reads the width of the image from the metadata
     * @param metadata The image metadata
     * @return The width (pixels)
     * @throws ImageReadException if the tag cannot be read
     */
    private int readWidth(JpegImageMetadata metadata) throws ImageReadException {
        TiffField field = metadata.findEXIFValue(TiffTagConstants.TIFF_TAG_IMAGE_WIDTH);
        return field.getIntValue();
    }

    /**
     * Reads the width from the image information
     * @param imageInfo The image information
     * @return The width of the image (pixels)
     */
    private int readWidth(ImageInfo imageInfo) {
        return imageInfo.getWidth();
    }

    /**
     * Reads the thumbnail from the metadata
     * @param metadata The image metadata
     * @return The thumbnail
     * @throws IOException if the file cannot be accessed
     * @throws ImageReadException if the thumbnail cannot be read from the image
     */
    private BufferedImage getThumbnail(JpegImageMetadata metadata) throws IOException, ImageReadException {
        return metadata.getEXIFThumbnail();
    }

    /**
     * Creates a thumbnail from the image file by scaling it
     * @param imageInfo the image information
     * @return A scaled down version of the picture with a width of 150 px
     * @throws IOException if the file cannot be accessed
     */
    private BufferedImage getThumbnail(ImageInfo imageInfo) throws IOException {
        int width = 150;
        double factor = imageInfo.getWidth() / (double) width;
        int height = (int) (imageInfo.getHeight() / factor);

        Image originalImage;
        try {
            originalImage = Imaging.getBufferedImage(this.imageFile);
        } catch (ImageReadException e) {
            originalImage = ImageIO.read(this.imageFile);
        }
        Image scaledImage = originalImage.getScaledInstance(width, height, BufferedImage.SCALE_SMOOTH);
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        buffered.getGraphics().drawImage(scaledImage, 0, 0 , null);
        return buffered;
    }

    //endregion
    //region renaming

    /**
     * Returns whether a file with the new name already exists.
     * @return
     */
    protected boolean renamedFileExists() {
        return this.renamedImageFile.exists();
    }

    /**
     * Resolves naming conflicts by appending an index to the filename
     */
    private void resolveNamingConflict() {
        String requestedName = this.newName.getValue();
        for(int i=1; renamedFileExists(); i++) {
            this.newName.setValue(String.format("%s_%d", requestedName, i));
        }
    }

    /**
     * Renames the file to the given new name
     * @throws IOException if the file cannot be renamed
     */
    protected void renameFile() throws IOException {
        this.renameFile(false);
    }

    /**
     * Renames the file to the given new name. Optionally resolves naming conflicts
     * @param resolveConflict determines, whether conflicts should be resolved
     * @throws IOException if the file cannot be renamed
     */
    protected void renameFile(boolean resolveConflict) throws IOException {
        if(this.imageFile.getAbsolutePath().equalsIgnoreCase(this.renamedImageFile.getAbsolutePath())) {
           return;
        }
        if(renamedFileExists()) {
            if(resolveConflict) {
                this.resolveNamingConflict();
            } else {
                throw new IOException(String.format("The file at \"%s\" already exists.", this.renamedImageFile.getAbsolutePath()));
            }
        }
        Files.move(this.imageFile.toPath(), this.renamedImageFile.toPath());
        this.setImageFile(this.renamedImageFile);
    }
    //endregion
}
