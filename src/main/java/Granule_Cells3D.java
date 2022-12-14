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



public class Granule_Cells3D implements PlugIn {
    
    Tools tools = new Tools();
    private boolean canceled = false;
    private String imageDir = "";
    public String outDirResults = "";
    private BufferedWriter outPutResults;
    
    
    public void run(String arg) {
        try {
            FileWriter fwResults = null;
            if (canceled) {
                IJ.showMessage("Plugin canceled");
                return;
            }
            if ((!tools.checkInstalledModules())) {
                return;
            }
            imageDir = IJ.getDirectory("Choose directory containing image files...");
            if (imageDir == null) {
                return;
            }
            // Find images with file_ext extension
            String file_ext = "nd";
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
            String header = "Image name\tROI name\tRoi volume (µm3)\tNb nuclei\n";
            fwResults = new FileWriter(outDirResults + "results.xls", false);
            outPutResults = new BufferedWriter(fwResults);
            outPutResults.write(header);
            outPutResults.flush();
            
            // create OME-XML metadata store of the latest schema version
            ServiceFactory factory;
            factory = new ServiceFactory();
            OMEXMLService service = factory.getInstance(OMEXMLService.class);
            IMetadata meta = service.createOMEXMLMetadata();
            ImageProcessorReader reader = new ImageProcessorReader();
            reader.setMetadataStore(meta);
            // Find calibration
            reader.setId(imageFiles.get(0));
            tools.cal = tools.findImageCalib(meta);
            
            for (String f : imageFiles) {
                String rootName = FilenameUtils.getBaseName(f);
                // find rois
                String roiFile = imageDir+rootName+".roi";
                if (!new File(roiFile).exists())
                    roiFile = imageDir+rootName+".zip";
                if (!new File(roiFile).exists()) {
                    IJ.showMessage("Error", "No roi file found");
                    return;
                }
                ImporterOptions options = new ImporterOptions();
                options.setId(f);
                options.setSplitChannels(true);
                options.setQuiet(true);
                options.setColorMode(ImporterOptions.COLOR_MODE_GRAYSCALE);
                // Find roi(s)
                RoiManager rm = new RoiManager(false);
                rm.runCommand("Open", roiFile);
                Roi[] rois = rm.getRoisAsArray();
                for (Roi roi : rois) {
                    String roiName = roi.getName();
                    options.setCrop(true);
                    Region reg = new Region(roi.getBounds().x, roi.getBounds().y, roi.getBounds().width, roi.getBounds().height);
                    options.setCropRegion(0, reg);
                    options.doCrop();
                    tools.print("--- ANALYZING IMAGE " + rootName + " ------");
                    
                    // Open Hoechst channel
                    tools.print("- Analyzing DAPI channel -");
                    ImagePlus imgNuc = BF.openImagePlus(options)[0];
                    
                    // Find number of nuclei
                    System.out.println("Finding nuclei....");
                    double nbNuclei = tools.getNbNuclei(imgNuc, roi);
                    System.out.println(nbNuclei + " nuclei found in roi "+roiName);
                    
                    // Compute roi volume
                    double roiVol = tools.roiVol(roi, imgNuc);
                    
                    // Write results
                    outPutResults.write(rootName+"\t"+roiName+"\t"+roiVol+"\t"+nbNuclei+"\n");
                    outPutResults.flush();
                    
                    tools.flush_close(imgNuc);
                }
            }
            outPutResults.close();
            System.out.println("Process done");
        } catch (IOException | DependencyException | ServiceException | FormatException ex) {
            Logger.getLogger(Granule_Cells3D.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
