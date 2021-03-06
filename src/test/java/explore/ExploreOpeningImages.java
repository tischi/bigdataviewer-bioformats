package explore;

import ch.epfl.biop.bdv.bioformats.BioFormatsOpenPlugInSciJava;
import ch.epfl.biop.bdv.bioformats.Units;
import net.imagej.ImageJ;

import java.io.File;

public class ExploreOpeningImages
{
	public static void main( String[] args )
	{
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();

		final BioFormatsOpenPlugInSciJava command = new BioFormatsOpenPlugInSciJava();
		command.cs = ij.command();
		command.createNewWindow = true;
		command.inputFile = new File("/Users/tischer/Desktop/20x_g5_a1.nd2");
		command.ignoreMetadata = false;
		command.unit = Units.MICRONS;
		command.run();

		final BioFormatsOpenPlugInSciJava command2 = new BioFormatsOpenPlugInSciJava();
		command2.bdv_h = command.bdv_h;
		command2.cs = ij.command();
		command2.createNewWindow = false;
		command2.inputFile = new File("/Users/tischer/Desktop/60x_g5_a1.nd2");
		command2.ignoreMetadata = false;
		command.unit = Units.MICRONS;
		command2.run();
	}
}
