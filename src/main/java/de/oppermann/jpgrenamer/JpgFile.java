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
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfo;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoAscii;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoShort;

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

    private static final String EXIF_DATE_PATTERN = "yyyy:MM:dd HH:mm:ss";

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
        ImageInfo info = this.readImageInfo();

        //load metadata either from JpegMetadata or from ImageInfo
        this.taken = readDate(metadata);
        this.width = readWidth(metadata, info);
        this.height = readHeight(metadata, info);
        this.thumbnail = getThumbnail(metadata, info);

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
     * Reads the date of the image. If no metadata is present or the metadata does not contain the date,
     * the file creation date is used.
     * @param metadata The image metadata, allowed to be null
     * @return The creation date
     * @throws IOException if the file cannot be accessed
     */
    private Date readDate(JpegImageMetadata metadata) throws IOException {
        Date dateTaken = metadata != null? readDateFromTag(metadata) : null;
        if(dateTaken == null) {
            dateTaken = readFileDate();
        }
        return dateTaken;
    }

    /**
     * Reads the date from the Jpeg metadata. If the "DateTimeOriginal" tag is present,
     * it is used. Otherwise, the DateTime tag is used.
     * @param metadata The image metadata
     * @return The date parsed from the tag information
     */
    private Date readDateFromTag(JpegImageMetadata metadata) {
        Date dateTaken = readDateFromTag(metadata, new TagInfoAscii("DateTimeOriginal", 36867, 20, TiffDirectoryType.EXIF_DIRECTORY_EXIF_IFD));
        if(dateTaken == null) {
            dateTaken = readDateFromTag(metadata, TiffTagConstants.TIFF_TAG_DATE_TIME);
        }
        return dateTaken;
    }

    /**
     * Reads the date from the specified tag of the given metadata
     * @param metadata The image metadata
     * @param tag The tag to read the date from
     * @return The date, or null, if no date can be read from the tag
     */
    private Date readDateFromTag(JpegImageMetadata metadata, TagInfo tag) {
        try {
            //try reading the original date tag
            TiffField field = metadata.findEXIFValue(tag);
            if(field == null) {
                return null;
            }
            SimpleDateFormat dateFormat = new SimpleDateFormat(EXIF_DATE_PATTERN);
            return dateFormat.parse(field.getStringValue());
        } catch (ImageReadException | ParseException e) {
            return null;
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
     * Reads the height of the image. If no metadata is available, the image info is used.
     * @param metadata The metadata of the image
     * @param imageInfo The image info
     * @return The height of the image (px), or -1 if no information is available
     */
    private int readHeight(JpegImageMetadata metadata, ImageInfo imageInfo) {
        int height = metadata != null? readHeight(metadata): -1;
        if(height == -1) {
            height = readHeight(imageInfo);
        }
        return height;
    }

    /**
     * Reads the height of the image from the metadata
     * @param metadata The image metadata
     * @return The height (pixels)
     */
    private int readHeight(JpegImageMetadata metadata) {
        try {
            TiffField field = metadata.findEXIFValue(TiffTagConstants.TIFF_TAG_IMAGE_LENGTH);
            if(field != null) {
                return field.getIntValue();
            }
            field = metadata.findEXIFValue(new TagInfoShort("ExifImageLength", 40963, TiffDirectoryType.EXIF_DIRECTORY_EXIF_IFD));
            if(field != null) {
                return field.getIntValue();
            }
            return -1;
        } catch (ImageReadException e) {
            return -1;
        }
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
     * Reads the width of the image. If no metadata is available, the image info is used.
     * @param metadata The metadata of the image
     * @param imageInfo The image info
     * @return The width of the image (px), or -1 if no information is available
     */
    private int readWidth(JpegImageMetadata metadata, ImageInfo imageInfo) {
        int width = metadata != null? readWidth(metadata): -1;
        if(width == -1) {
            width = readWidth(imageInfo);
        }
        return width;
    }

    /**
     * Reads the width of the image from the metadata
     * @param metadata The image metadata
     * @return The width (pixels)
     */
    private int readWidth(JpegImageMetadata metadata) {
        try {
            TiffField field = metadata.findEXIFValue(TiffTagConstants.TIFF_TAG_IMAGE_WIDTH);
            if(field != null) {
                return field.getIntValue();
            }
            field = metadata.findEXIFValue(new TagInfoShort("ExifImageWidth", 40962, TiffDirectoryType.EXIF_DIRECTORY_EXIF_IFD));
            if(field != null) {
                return field.getIntValue();
            }
            return -1;
        } catch (ImageReadException e) {
            return -1;
        }
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
     * Reads the thumbnail of the image from the metadata.
     * If no metadata thumbnail is available, the image is scaled and returned.
     * @param metadata The image metadata
     * @param imageInfo The image information
     * @return A buffered image containing the thumbnail, or null, if no image data is available.
     */
    private BufferedImage getThumbnail(JpegImageMetadata metadata, ImageInfo imageInfo) {
        BufferedImage thumbnail = metadata != null? getThumbnail(metadata) : null;
        if(thumbnail == null) {
            thumbnail = getThumbnail(imageInfo);
        }
        return thumbnail;
    }

    /**
     * Reads the thumbnail from the metadata
     * @param metadata The image metadata
     * @return The thumbnail
     */
    private BufferedImage getThumbnail(JpegImageMetadata metadata){
        try {
            return metadata.getEXIFThumbnail();
        } catch (ImageReadException | IOException e) {
            return null;
        }
    }

    /**
     * Creates a thumbnail from the image file by scaling it
     * @param imageInfo the image information
     * @return A scaled down version of the picture with a width of 150 px
     */
    private BufferedImage getThumbnail(ImageInfo imageInfo) {
        int width = 150;
        double factor = imageInfo.getWidth() / (double) width;
        int height = (int) (imageInfo.getHeight() / factor);

        Image originalImage;
        try {
            originalImage = Imaging.getBufferedImage(this.imageFile);
        } catch (ImageReadException | IOException e) {
            try {
                originalImage = ImageIO.read(this.imageFile);
            } catch (IOException ex) {
                return null;
            }
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
     * @return true if the file exists
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
