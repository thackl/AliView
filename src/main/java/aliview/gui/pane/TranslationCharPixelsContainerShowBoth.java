package aliview.gui.pane;

import java.awt.Color;
import java.awt.Font;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.log4j.Logger;

import aliview.AminoAcid;
import aliview.NucleotideUtilities;
import aliview.alignment.Alignment;
import aliview.color.ColorScheme;
import aliview.sequencelist.AlignmentListModel;


public class TranslationCharPixelsContainerShowBoth {
	private static final Logger logger = Logger.getLogger(TranslationCharPixelsContainerShowBoth.class);
	private CharPixelsContainer[] allAAContainers;

	public RGBArray getRGBArray(AminoAcid aa, byte residue){
		return allAAContainers[aa.intVal].getRGBArray(residue);
	}


	public static TranslationCharPixelsContainerShowBoth createDefaultTranslationPixelsContainer(Font font, int minFontSize, int width, int height, ColorScheme colorScheme, int fontCase) {
		TranslationCharPixelsContainerShowBoth transContainer = new TranslationCharPixelsContainerShowBoth();
		transContainer.allAAContainers = new CharPixelsContainer[AminoAcid.HIGEST_AA_INT_VAL + 1];
		for(AminoAcid containerAcid: AminoAcid.GROUP_ALL){
			CharPixelsContainer container = new CharPixelsContainer();
			for(int n = 0; n < container.backend.length; n++){	
				int baseVal = NucleotideUtilities.baseValFromBase((byte)n);
				Color fgColor = colorScheme.getBaseForegroundColor(baseVal);
				Color bgColor = colorScheme.getAminoAcidBackgroundColor(containerAcid);
				container.backend[n] = new CharPixels((char)n, width, height, fgColor, bgColor, font, minFontSize, fontCase);
			}
			transContainer.allAAContainers[containerAcid.intVal] = container;
		}
		return transContainer;
	}

	public static TranslationCharPixelsContainerShowBoth createSelectedTranslationPixelsContainer(Font font, int minFontSize, int width, int height, ColorScheme colorScheme, int fontCase) {
		TranslationCharPixelsContainerShowBoth transContainer = new TranslationCharPixelsContainerShowBoth();
		transContainer.allAAContainers = new CharPixelsContainer[AminoAcid.HIGEST_AA_INT_VAL + 1];
		for(AminoAcid containerAcid: AminoAcid.GROUP_ALL){
			CharPixelsContainer container = new CharPixelsContainer();
			for(int n = 0; n < container.backend.length; n++){	
				int baseVal = NucleotideUtilities.baseValFromBase((byte)n);
				// It looks better without the selected color for foreground
				Color fgColor = colorScheme.getBaseForegroundColor(baseVal);
				Color bgColor = colorScheme.getAminoAcidSelectionBackgroundColor(containerAcid);
				container.backend[n] = new CharPixels((char)n, width, height, fgColor, bgColor, font, minFontSize, fontCase);
			}
			transContainer.allAAContainers[containerAcid.intVal] = container;
		}
		return transContainer;
	}

	public static TranslationCharPixelsContainerShowBoth createLetterTranslationPixelsContainer(Font font, int minFontSize, int width, int height, ColorScheme colorScheme, int fontCase) {
		TranslationCharPixelsContainerShowBoth transContainer = new TranslationCharPixelsContainerShowBoth();
		transContainer.allAAContainers = new CharPixelsContainer[AminoAcid.HIGEST_AA_INT_VAL + 1];
		for(AminoAcid containerAcid: AminoAcid.GROUP_ALL){
			CharPixelsContainer container = new CharPixelsContainer();
			for(int n = 0; n < container.backend.length; n++){	
				Color fgColor = Color.white; //colorScheme.getAminoAcidForgroundColor(containerAcid);
				Color bgColor = colorScheme.getAminoAcidBackgroundColor(containerAcid);
				container.backend[n] = new CharPixels(containerAcid.getCodeCharVal(), width, height, fgColor, bgColor, font, minFontSize, fontCase);
			}
			transContainer.allAAContainers[containerAcid.intVal] = container;
		}
		return transContainer;
	}

	public static TranslationCharPixelsContainerShowBoth createSelectedLetterTranslationPixelsContainer(Font font, int minFontSize, int width, int height, ColorScheme colorScheme, int fontCase) {
		TranslationCharPixelsContainerShowBoth transContainer = new TranslationCharPixelsContainerShowBoth();
		transContainer.allAAContainers = new CharPixelsContainer[AminoAcid.HIGEST_AA_INT_VAL + 1];
		for(AminoAcid containerAcid: AminoAcid.GROUP_ALL){
			CharPixelsContainer container = new CharPixelsContainer();
			for(int n = 0; n < container.backend.length; n++){	
				Color fgColor = Color.white; //colorScheme.getAminoAcidForgroundColor(containerAcid);
				Color bgColor = colorScheme.getAminoAcidSelectionBackgroundColor(containerAcid);
				container.backend[n] = new CharPixels(containerAcid.getCodeCharVal(), width, height, fgColor, bgColor, font, minFontSize, fontCase);
			}
			transContainer.allAAContainers[containerAcid.intVal] = container;
		}
		return transContainer;
	}

}