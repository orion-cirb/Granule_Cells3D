/*
 * In 3D images of granule cells, count nuclei
 * Author: ORION-CIRB
 */

import Granule_Cells3D_Tools.Tools;
import ij.*;
import ij.gui.Roi;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import loci.common.Region;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import loci.plugins.BF;
import loci.plugins.util.ImageProcessorReader;
import loci.plugins.in.ImporterOptions;
import org.apache.commons.io.FilenameUtils;
import org.scijava.util.ArrayUtils;



public class Granule_Cells3D implements PlugIn {
    
    Tools tools = new Tools();
    private String imageDir = "";
    public String outDirResults = "";
    private BufferedWriter outPutResults;
    
    
    public void run(String arg) {
        try {
            if ((!tools.checkInstalledModules())) {
                return;
            }
            
            imageDir = IJ.getDirectory("Choose directory containing image files...");
            if (imageDir == null) {
                return;
            }
            
            // Find images with file_ext extension
            String file_ext = tools.findImageType(new File(imageDir));
            ArrayList<String> imageFiles = tools.findImages(imageDir, file_ext);
            if (imageFiles == null) {
                IJ.showMessage("Error", "No images found with " + file_ext + " extension");
                return;
            }
            
            // Create output folder
            outDirResults = imageDir + File.separator + "Results" + File.separator;
            File outDir = new File(outDirResults);
            if (!Files.exists(Paths.get(outDirResults))) {
                outDir.mkdir();
            }
            
            // Write header in results file
            String header = "Image name\tROI name\tROI volume (µm3)\tNb nuclei\n";
            FileWriter fwResults = new FileWriter(outDirResults + "results.xls", false);
            outPutResults = new BufferedWriter(fwResults);
            outPutResults.write(header);
            outPutResults.flush();
            
            // Create OME-XML metadata store of the latest schema version
            ServiceFactory factory;
            factory = new ServiceFactory();
            OMEXMLService service = factory.getInstance(OMEXMLService.class);
            IMetadata meta = service.createOMEXMLMetadata();
            ImageProcessorReader reader = new ImageProcessorReader();
            reader.setMetadataStore(meta);
            reader.setId(imageFiles.get(0));
            
            // Find image calibration
            tools.findImageCalib(meta);
            
            // Find channels name
            String[] channels = tools.findChannels(imageFiles.get(0), meta, reader);
            
            // Dialog box
            String[] chs = tools.dialog(channels);
            if (chs == null) {
                IJ.showMessage("Error", "Plugin canceled");
                return;
            }
            
            for (String f : imageFiles) {
                String rootName = FilenameUtils.getBaseName(f);
                tools.print("--- ANALYZING IMAGE " + rootName + " ------");
                
                ImporterOptions options = new ImporterOptions();
                options.setId(f);
                options.setSplitChannels(true);
                options.setQuiet(true);
                options.setColorMode(ImporterOptions.COLOR_MODE_GRAYSCALE);
                
                // Find ROI(s)
                String roiFile = imageDir+rootName+".roi";
                if (!new File(roiFile).exists())
                    roiFile = imageDir+rootName+".zip";
                if (!new File(roiFile).exists()) {
                    tools.print("ERROR: No ROI file found for image " + rootName);
                    IJ.showMessage("Error", "No ROI file found for image " + rootName);
                    continue;
                }
                
                RoiManager rm = new RoiManager(false);
                rm.runCommand("Open", roiFile);
                Roi[] rois = rm.getRoisAsArray();
                for (Roi roi: rois) {
                    String roiName = roi.getName();
                    tools.print("- Analyzing ROI " + roiName + " -");
                    
                    Region reg = new Region(roi.getBounds().x, roi.getBounds().y, roi.getBounds().width, roi.getBounds().height);
                    options.setCrop(true);
                    options.setCropRegion(0, reg);
                    options.doCrop();
                    
                    // Open Hoechst channel
                    tools.print("Opening nuclei channel...");
                    int indexCh = ArrayUtils.indexOf(channels, chs[0]);
                    ImagePlus imgNuc = BF.openImagePlus(options)[indexCh];
                    
                    // Compute nuclei number
                    tools.print("Counting nuclei...");
                    int nbNuclei = tools.getNbNuclei(imgNuc, roi);
                    System.out.println(nbNuclei + " nuclei found");
                    
                    // Compute ROI volume
                    double roiVol = tools.roiVol(roi, imgNuc);
                                        
                    // Write results
                    outPutResults.write(rootName+"\t"+roiName+"\t"+roiVol+"\t"+nbNuclei+"\n");
                    outPutResults.flush();
                    
                    tools.flush_close(imgNuc);
                }
            }
            
            outPutResults.close();
            tools.print("--- All done! ---");
            
        } catch (IOException | DependencyException | ServiceException | FormatException ex) {
            Logger.getLogger(Granule_Cells3D.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
