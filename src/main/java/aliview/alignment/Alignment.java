package aliview.alignment;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import utils.DialogUtils;
import utils.nexus.CharSet;
import utils.nexus.Excludes;
import utils.nexus.NexusRange;
import utils.nexus.NexusUtilities;
import aliview.AATranslator;
import aliview.AliViewExtraNexusUtilities;
import aliview.Base;
import aliview.FileFormat;
import aliview.GeneticCode;
import aliview.NucleotideUtilities;
import aliview.importer.AlignmentImportException;
import aliview.importer.SequencesFactory;
import aliview.messenges.TextEditDialog;
import aliview.primer.Dimer;
import aliview.primer.Primer;
import aliview.sequencelist.FileSequenceListModel;
import aliview.sequencelist.FindObject;
import aliview.sequencelist.MemorySequenceListModel;
import aliview.sequencelist.SequenceListModel;
import aliview.sequencelist.FileSequenceLoadListener;
import aliview.sequences.PhylipSequence;
import aliview.sequences.Sequence;
import aliview.sequences.SequenceUtils;
import aliview.settings.Settings;

public class Alignment implements FileSequenceLoadListener {
	private static final Logger logger = Logger.getLogger(Alignment.class);
	private static final String LF = System.getProperty("line.separator");
	private static final SequencesFactory seqFactory = new SequencesFactory();
	SequenceListModel sequences;
	protected int nextFindSequenceNumber;
	protected int nextFindStartPos;
	private File alignmentFile;
	private int PRIMER_MAX_DEGENERATE_SCORE = 1000;
	private boolean isEditedAfterLastSave = false;
	private FileFormat fileFormat;
	private AlignmentMeta alignmentMeta;
	private int readingFrame = 1;
	private GeneticCode geneticCode = GeneticCode.DEFAULT;
	private boolean editMode;
	private AliHistogram cachedHistogram;
	private ArrayList<AlignmentListener> alignmentListeners = new ArrayList<AlignmentListener>();
	private boolean showTranslationOnePos;
	private boolean isSelectable;
	private AliHistogram translatedHistogram;

	public Alignment() {
		this.sequences = new MemorySequenceListModel();
		this.alignmentMeta = new AlignmentMeta(sequences.getLongestSequenceLength());
		setEditedAfterLastSave(false);
		fireNewSequences();
	}

	public Alignment(File file, FileFormat fileFormat, SequenceListModel sequences, AlignmentMeta aliMeta) {
		this.alignmentFile = file;
		this.fileFormat = fileFormat;
		this.sequences = sequences;
		this.alignmentMeta = aliMeta;
		setEditedAfterLastSave(false);
		if(this.sequences instanceof FileSequenceListModel){
			((FileSequenceListModel) sequences).addSequenceLoadLisetner(this);
		}
		fireNewSequences();
	}
	
	public void addAlignmentListener(AlignmentListener listener){
		alignmentListeners .add(listener);
	}
	
	private void fireSequencesChanged(){
		cachedHistogram = null;
		for(AlignmentListener listener: alignmentListeners){
			listener.sequencesChanged(new AlignmentEvent(this));
		}
	}
	private void fireSelectionChanged(){
		for(AlignmentListener listener: alignmentListeners){
			listener.selectionChanged(this);
		}
	}
	
	private void fireNewSequences(){
		cachedHistogram = null;
		for(AlignmentListener listener: alignmentListeners){
			listener.newSequences(new AlignmentEvent(this));
		}
	}
	
	private void fireSequenceOrderChanged(){
		for(AlignmentListener listener: alignmentListeners){
			listener.sequenceOrderChanged(new AlignmentEvent(this));
		}	
	}
	
	private void fireAlignmentMetaChanged(){
		cachedHistogram = null;
		for(AlignmentListener listener: alignmentListeners){
			listener.alignmentMetaChanged(new AlignmentEvent(this));
		}
	}
	
	private void fireSequencesRemoved(){
		cachedHistogram = null;
		for(AlignmentListener listener: alignmentListeners){
			listener.sequencesRemoved(new AlignmentEvent(this));
		}
	}
	

	public Alignment(SequenceListModel sequences) {
		this.sequences = sequences;
		this.alignmentMeta = new AlignmentMeta(this.getMaximumSequenceLength());
		fireNewSequences();
	}
	
	public void setNewSequences(SequenceListModel seqs){
		this.sequences = seqs;			
		// TODO this should also be read from file
		this.alignmentMeta = new AlignmentMeta(this.getMaximumSequenceLength());	
		setEditedAfterLastSave(false);
		fireNewSequences();
	}
	
	public int getMaxY() {
		return sequences.getSize();
	}

	public byte getBaseAt(int x, int y) {
		return sequences.getBaseAt(x,y);
	}

	public int getLengthAt(int y) {
		return sequences.getLengthAt(y);
	}

	public int getMaxX() {
		return sequences.getLongestSequenceLength();
	}
	
	// TODO this is the only place where sequences are checked for modification when they are "outside"
	public SequenceListModel getSequences() {
		return sequences;
	}
	

	public int getMaximumSequenceLength() {
		return getMaxX();
	}

	public int getSize(){
		return sequences.getSize();
	}

	public void clearSelection() {
		sequences.clearSelection();
		fireSelectionChanged();
	}

	public int getSequencePosition(Sequence seq) {
		return sequences.indexOf(seq);
	}
	
	public File getAlignmentFile() {
		return this.alignmentFile;
	}

	public String getFileName() {
		if(alignmentFile == null){
			return null;
		}
		return alignmentFile.getName();

	}

	public void setAlignmentFile(File alignmentFile) {
		if(alignmentFile != null && alignmentFile.exists()){
			this.alignmentFile = alignmentFile;
			fireNewSequences();
		}
	}
	
	public void storeAlignmetAsFasta(Writer out) throws IOException{
		int longest = sequences.getLongestSequenceLength();
		for(Sequence seq: sequences){
			String name = seq.getName();

			out.write('>');
			out.write(seq.getName());
			out.write(LF);
			seq.writeBases(out);
					
			out.write(LF);
		}		
		out.flush();
		out.close();
	}


	private void storeAlignmetAsPhyFile(Writer out) throws IOException{
		// First line number of seq + seqLen
		out.write("" + sequences.getSize() + " " + this.getMaximumSequenceLength() + LF);

		for(Sequence seq: sequences){			
			out.write("" + StringUtils.rightPad(seq.getName(),100) + "");
			seq.writeBases(out);
			out.write(LF);
		}

		out.flush();
		out.close();	

	}

	private void storeAlignmetAsPhyTranslatedAminoAcidFile(Writer out) throws IOException{
		// First line number of seq + seqLen
		out.write("" + sequences.getSize() + " " + alignmentMeta.getCodonPositions().getTranslatedAminAcidLength() + LF);

		AATranslator aaTransSeq = new AATranslator(getAlignentMeta().getCodonPositions(),getGeneticCode());
		for(Sequence seq: sequences){
			aaTransSeq.setSequence(seq);
		
			out.write("" + StringUtils.rightPad(seq.getName(),100));
			aaTransSeq.writeTranslation(out);
			out.write(LF);
		}

		out.flush();
		out.close();	

	}
	
	private void storeAlignmetAsFastaTranslatedAminoAcidFile(Writer out) throws IOException{	
		AATranslator aaTransSeq = new AATranslator(getAlignentMeta().getCodonPositions(),getGeneticCode());
		for(Sequence seq: sequences){
			aaTransSeq.setSequence(seq);
			
			String name = seq.getName();
			out.write('>');
			out.write(seq.getName());
			out.write(LF);
			
			aaTransSeq.writeTranslation(out);
			out.write(LF);
		}		
		out.flush();
		out.close();
	}

	private void storeMetaData(BufferedWriter outMeta) throws IOException {
		outMeta.write("" + NexusUtilities.getExcludesAsNexusBlock(alignmentMeta.getExcludes()));
		outMeta.write(LF);
		outMeta.write("" + NexusUtilities.getCodonPosAsNexusBlock(alignmentMeta.getCodonPositions()));
		outMeta.write(LF);
		outMeta.write("" + NexusUtilities.getCharsetsBlockAsNexus(alignmentMeta.getCharsets()));
		outMeta.write(LF);

		outMeta.flush();
		outMeta.close();
	}

	private void storeTranslatedMetaData(BufferedWriter outMeta, AlignmentMeta translatedMeta) throws IOException {
		outMeta.write("" + NexusUtilities.getExcludesAsNexusBlock(translatedMeta.getExcludes()));
		outMeta.write(LF);
		//outMeta.write("" + NexusUtilities.getCodonPosAsNexusBlock(alignmentMeta.getCodonPositions()));
		outMeta.write("" + NexusUtilities.getCharsetsBlockAsNexus(translatedMeta.getCharsets()));
		outMeta.write(LF);
		outMeta.flush();
		outMeta.close();
	}

	public void saveSelectionAsFastaFile(File selectedFile){
		
		try {
			BufferedWriter buffOut = new BufferedWriter(new FileWriter(selectedFile));
			sequences.writeSelectionAsFasta(buffOut);
			buffOut.flush();
			buffOut.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void saveAlignmentAsFile(File outFile) throws IOException{	
		// save as current format
		saveAlignmentAsFile(outFile,this.fileFormat);	
	}

	public void saveAlignmentAsFile(File outFile, FileFormat fileFormat) throws IOException{
		
		// make sure they are equal length
		sequences.rightPadWithGapUntilEqualLength();
		sequences.rightTrimSequencesRemoveGapsUntilEqualLength();
		
		BufferedWriter out = new BufferedWriter(new FileWriter(outFile));

		int nexusDatatype = AliViewExtraNexusUtilities.DATATYPE_DNA;
		if(sequences.getSequenceType() == SequenceUtils.TYPE_AMINO_ACID){
			nexusDatatype = AliViewExtraNexusUtilities.DATATYPE_PROTEIN;
		}
		
		if(fileFormat == FileFormat.FASTA){
			storeAlignmetAsFasta(out);
			// save meta if exset it is set
			if(this.alignmentMeta.isMetaOutputNeeded()){
				BufferedWriter outMeta = new BufferedWriter(new FileWriter(new File(outFile.getAbsoluteFile() + ".meta")));
				storeMetaData(outMeta);
			}
		}else if(fileFormat == FileFormat.PHYLIP){
			storeAlignmetAsPhyFile(out);
			// save meta if exset it is set
			if(this.alignmentMeta.isMetaOutputNeeded()){
				BufferedWriter outMeta = new BufferedWriter(new FileWriter(new File(outFile.getAbsoluteFile() + ".meta")));
				storeMetaData(outMeta);
			}
		}else if(fileFormat == FileFormat.PHYLIP_TRANSLATED_AMINO_ACID){
			storeAlignmetAsPhyTranslatedAminoAcidFile(out);
			// save meta if exset it is set
			if(this.alignmentMeta.isMetaOutputNeeded()){
				AlignmentMeta translatedMeta = getTranslatedMeta();
				BufferedWriter outMeta = new BufferedWriter(new FileWriter(new File(outFile.getAbsoluteFile() + ".meta")));
				storeTranslatedMetaData(outMeta, translatedMeta);
			}
		}else if(fileFormat == FileFormat.FASTA_TRANSLATED_AMINO_ACID){
			storeAlignmetAsFastaTranslatedAminoAcidFile(out);
			// save meta if exset it is set
			if(this.alignmentMeta.isMetaOutputNeeded()){
				AlignmentMeta translatedMeta = getTranslatedMeta();
				BufferedWriter outMeta = new BufferedWriter(new FileWriter(new File(outFile.getAbsoluteFile() + ".meta")));
				storeTranslatedMetaData(outMeta, translatedMeta);
			}
		}else if(fileFormat == FileFormat.NEXUS){
			AliViewExtraNexusUtilities.exportAlignmentAsNexus(new BufferedWriter(new FileWriter(outFile)), this, false,nexusDatatype);
		}else if(fileFormat == FileFormat.NEXUS_TRANSLATED_AMINO_ACID){
			AliViewExtraNexusUtilities.exportAlignmentAsNexus(new BufferedWriter(new FileWriter(outFile)), getTranslatedAlignment(), false, AliViewExtraNexusUtilities.DATATYPE_PROTEIN);
		}else if(fileFormat == FileFormat.NEXUS_SIMPLE){
			AliViewExtraNexusUtilities.exportAlignmentAsNexus(new BufferedWriter(new FileWriter(outFile)), this, true, nexusDatatype);
		}else if(fileFormat == FileFormat.NEXUS_CODONPOS_CHARSET){
			AliViewExtraNexusUtilities.exportAlignmentAsNexusCodonpos(new BufferedWriter(new FileWriter(outFile)), this, AliViewExtraNexusUtilities.DATATYPE_DNA);
		}		
	}
	
	/*
	 * utility method
	 */
	private String translateSequence(Sequence seq){
		AATranslator aaTransSeq = new AATranslator(getAlignentMeta().getCodonPositions(),getGeneticCode());
		aaTransSeq.setSequence(seq);
		return aaTransSeq.getTranslatedAsString();
	}

	private Alignment getTranslatedAlignment(){
		AlignmentMeta transMeta = getTranslatedMeta();
		SequenceListModel transSeq = createTranslatedSequences();
		Alignment translatedAlignment = new Alignment(null, null, transSeq, transMeta);
		return translatedAlignment;
	}
	
	//
	//  TODO this is not working for fileSequences
	//
	private SequenceListModel createTranslatedSequences(){
		SequenceListModel transSeqs = new MemorySequenceListModel();
		for(Sequence seq: sequences){			
			transSeqs.add(new PhylipSequence(seq.getName(), translateSequence(seq)));
		}
		return transSeqs;
	}


	// TODO this might get wrong if translating sequence and sequence order in alignment not is same, or sequence 
	// has x offset in alignment
	private AlignmentMeta getTranslatedMeta(){	
		ArrayList<CharSet> charsetsTrans = new ArrayList<CharSet>();
		Excludes excludesTrans = new Excludes(alignmentMeta.getCodonPositions().getTranslatedAminAcidLength());
		AlignmentMeta metaTrans = new AlignmentMeta(excludesTrans,null,charsetsTrans);
		
		for(CharSet charset: alignmentMeta.getCharsets()){
			ArrayList<NexusRange> nexusRanges = charset.getCharSetAsNexusRanges();
			if(nexusRanges != null){
				logger.info("rangesSize" + nexusRanges.size());
				CharSet translatedSet = new CharSet(charset.getName(),alignmentMeta.getCodonPositions().getTranslatedAminAcidLength());
				for(NexusRange range: nexusRanges){	
					NexusRange translatedRange = new NexusRange(alignmentMeta.getCodonPositions().getAminoAcidPosFromNucleotidePos(range.getMinimumInteger()),
							                                         alignmentMeta.getCodonPositions().getAminoAcidPosFromNucleotidePos(range.getMaximumInteger()));
					logger.info(translatedRange);
					translatedSet.addRange(translatedRange);
				}
				charsetsTrans.add(translatedSet);
			}
		}
		for(NexusRange range: alignmentMeta.getExcludes().getExcludedAsNexusRanges()){
			NexusRange translatedRange = new NexusRange(alignmentMeta.getCodonPositions().getAminoAcidPosFromNucleotidePos(range.getMinimumInteger()),
					                                         alignmentMeta.getCodonPositions().getAminoAcidPosFromNucleotidePos(range.getMaximumInteger()));
			excludesTrans.addRange(translatedRange);
		}
		return metaTrans;
	}
	
	
	
	
	/*
	 * Alternate method
	 */
	public boolean mergeTwoSelected(Sequence[] selected, boolean allowOverlap) {
		boolean isMerged = false;
		if(selected.length == 2){
			isMerged = mergeTwoSequences(selected[0],selected[1], allowOverlap);
		}
		return isMerged;
	}
	

	public boolean mergeTwoSequences(Sequence seq1, Sequence seq2, boolean allowOverlap){
		boolean wasMerged = sequences.mergeTwoSequences(seq1, seq2, allowOverlap);
		if(wasMerged){
			fireNewSequences();
		}
		return wasMerged;
	}


	public void deleteAllExsetBases(){	
		// first create a deletemask
		boolean[] deleteMask = new boolean[this.alignmentMeta.getExcludes().getLength()];
		for(int n = 0; n < deleteMask.length; n++){
			if(this.alignmentMeta.isExcluded(n)){
				deleteMask[n] = true;
			}
		}
		sequences.deleteBasesInAllSequencesFromMask(deleteMask);
		//and finally remove in AlignmentMeta(excludes, codonpos & charset)
		alignmentMeta.removeFromMask(deleteMask);
		fireSequencesChanged();
	}



	public ArrayList<Primer> findPrimerInSelection(){
		ArrayList<Primer> allPrimers = new ArrayList<Primer>();
		if(! sequences.hasSelection()){
			return allPrimers;
		}
		
		String selection = getSelectionAsNucleotides();
		
		logger.info(selection);
		
		String[] selectionSeq = selection.split("\\n");

		// Start with a prototype as consensus
		int consensusVal[] = new int[selectionSeq[0].length()];

		for(String seq: selectionSeq){
			for(int n = 0; n < consensusVal.length; n++){
				int baseVal = NucleotideUtilities.baseValFromChar(seq.charAt(n));
				// Create consensus by bitwise OR of the bases in the same column 
				consensusVal[n] = consensusVal[n] | baseVal;
			}
		}

		// Create consensus from baseValues
		StringBuilder consensusBuilder = new StringBuilder();
		for(int n = 0; n < consensusVal.length; n++){
			consensusBuilder.append(NucleotideUtilities.charFromBaseVal(consensusVal[n]));
		}
		String consensus = consensusBuilder.toString();

		logger.info(consensus.length());
		
		// remove gaps in consensus
		consensus = consensus.replaceAll("\\-", "");

		// create primer put them in a list and sort list (on score)
		// create all primer min-max bases long 
		int primerMinLen = Settings.getMinPrimerLength().getIntValue();
		int primerMaxLen = Settings.getMaxPrimerLength().getIntValue();
		int primerMinTM = Settings.getPrimerMinTM().getIntValue();
		int primerMaxTM = Settings.getPrimerMaxTM().getIntValue();
		Dimer.setDimerLengthThreashold(Settings.getDimerReportThreashold().getIntValue());
		
		long nCount = 0;
		int selectionStartPos = getFirstSelectedPos();
		for(int winSize = primerMinLen; winSize <= primerMaxLen; winSize ++){

			int offset = 0;		
			int startPos = offset;
			int endPos = offset + winSize;

			logger.info(consensus.length());
			logger.info( endPos);

			while(endPos <= consensus.length()){
				String primerSeq = consensus.substring(startPos, endPos);
				Primer aPrimer = new Primer(primerSeq, selectionStartPos + startPos);
				// only add primers below score
				if(aPrimer.getDegenerateFold() <= PRIMER_MAX_DEGENERATE_SCORE && 
					aPrimer.getBaseStackingAvgTm() >= primerMinTM && aPrimer.getBaseStackingAvgTm() <= primerMaxTM){
					allPrimers.add(aPrimer);
				}
				offset ++;
				startPos = offset;
				endPos = offset + winSize;
				nCount ++;
			}
			logger.info("winSize=" + winSize);
		}
		logger.info("Primers tested:" + nCount);

		Collections.sort(allPrimers);


		return allPrimers;
		// https://ecom.mwgdna.com/services/webgist/mops.tcl?ot=OLIGO_ALC_UNMOD&oligoSequence=RTTGCTYRAKACTCGGTRA&ShowModiTables=OFF&oligo_name=&mod3=&mod5=&modx=&modz=&ot=OLIGO_ALC_UNMOD&oligo_name=&oligoSequence=RTT+GCT+YYY+RAK+ACT+CGG+TRA&action=properties&next_url=

	}
	
	/*
	 * 
	 * 	Selection section
	 * 
	 * 
	 */
	private int getFirstSelectedPos() {	
		return sequences.getFirstSelectedYPos();
	}

	private String getSelectionAsNucleotides(){
		return sequences.getSelectionAsNucleotides();
	}

	public void copySelectionToClipboardAsFasta(){
		StringWriter writer = new StringWriter();
		sequences.writeSelectionAsFasta(writer);
		// Set to clipboard
		StringSelection ss = new StringSelection(writer.toString());
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ss, null);
	}
	
	public void copySelectionToClipboardAsNucleotides(){
		// Set to clipboard
		StringSelection ss = new StringSelection(getSelectionAsNucleotides());
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ss, null);
	}
	
	public void copySelectionNames() {
		// Set to clipboard
		logger.info("copy sel names");
		String names = sequences.getSelectionNames();
		StringSelection ss = new StringSelection(names);
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ss, null);
		Toolkit.getDefaultToolkit().getSystemSelection().setContents(ss, null);
	}
	


	public boolean isBaseSelected(int x, int y) {
		return sequences.isBaseSelected(x, y);
	}


	public void setSelectionAt(int xPos, int yPos, boolean b) {
		sequences.setSelectionAt(xPos, yPos, b);
		fireSelectionChanged();
	}
	
	public void setSelectionWithin(Rectangle bounds, boolean isSelected) {
		sequences.setSelectionWithin(bounds, isSelected);
		fireSelectionChanged();
	}

	public void reverseComplementAlignment(){
		sequences.reverseComplement();
		alignmentMeta.reverse();
		fireSequencesChanged();
	}


	/*
	 * 
	 * Find
	 * 
	 */
	
	public FindObject findInNames(FindObject findObj) {
		return sequences.findInNames(findObj);	
	}
	
	public void clearFindLastPos() {
		nextFindSequenceNumber = 0;
		nextFindStartPos = 0;
	}
	
	public FindObject findInSequences(FindObject findObj){
		return sequences.findAndSelect(findObj);
	}
	
	public Sequence getSequenceByName(String name){
		return sequences.getSequenceByName(name);
	}

	
	public void incReadingFrame() {
		int newReadFrame = this.getReadingFrame();
		newReadFrame ++;
		if(newReadFrame > 3){
			newReadFrame = 1;
		}
		this.setReadingFrame(newReadFrame);
		fireAlignmentMetaChanged();
	}

	public int getReadingFrame() {
		return this.readingFrame;
	}

	public void decReadingFrame() {
		int newReadFrame = this.getReadingFrame();
		newReadFrame --;
		if(newReadFrame < 1){
			newReadFrame = 3;
		}
		this.setReadingFrame(newReadFrame);
		fireAlignmentMetaChanged();
	}


	public List<Sequence> clearSelectedBases(){
		List<Sequence> affected =  sequences.replaceSelectedBasesWithGap();
		if(affected.size() > 0){
			fireSequencesChanged();
		}
		return affected;
	}

	public List<Sequence> deleteSelectedBases() {
		List<Sequence> affected =  sequences.deleteSelectedBases();
		if(affected.size() > 0){
			fireSequencesChanged();
		}
		return affected;
	}

	public String getConsensus(){
		return sequences.getConsensus();
	}

	public void removeVerticalGaps(){
		String cons = getConsensus();
			
		// if there is a gap
		if(cons.indexOf(SequenceUtils.GAP_SYMBOL) > 0){		
			// create a bit-mask with pos to delete
			boolean[] deleteMask = new boolean[cons.length()];
			for(int n = 0; n < deleteMask.length; n++){
				if(cons.charAt(n) == SequenceUtils.GAP_SYMBOL){
					deleteMask[n] = true;
				}
			}	
			sequences.deleteBasesInAllSequencesFromMask(deleteMask);

			//and finally remove in AlignmentMeta(excludes, codonpos & charset)
			alignmentMeta.removeFromMask(deleteMask);
			fireSequencesChanged();
		}

	}

	public void addFasta(String clipboardSelection) {
		try {
			SequenceListModel sequencesFromClipboard = seqFactory.createFastaSequences(new StringReader(clipboardSelection));
			sequences.addAll(sequencesFromClipboard);
			fireNewSequences();
		} catch (AlignmentImportException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void addSequences(File additionalFile) {
		addSequences(additionalFile, 0);
	}
	
	public void addSequences(File additionalFile, int index) {
		try {
			SequenceListModel additionalSequences = seqFactory.createSequences(additionalFile);
			for(Sequence seq: additionalSequences){
				sequences.add(index, seq);
			}
			fireNewSequences();
		} catch (AlignmentImportException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	
	/*
	 * 
	 * 
	 * Start Aligner section
	 * 
	 * 
	 * 
	 */
	public void clearSelectionOffset() {
		sequences.setSelectionOffset(0);
	}
	
	public List<Sequence> moveSelectionRight(){
		logger.info("move");
		List<Sequence> previousState = sequences.moveSelectionRightIfGapIsPresent();
		if(previousState.size()> 0){
			fireSequencesChanged();
		}
		return previousState;
	}

	public List<Sequence> moveSelectionLeft() {
		List<Sequence> previousState = sequences.moveSelectionLeftIfGapIsPresent();
		if(previousState.size()> 0){
			fireSequencesChanged();
		}
		return previousState;
	}

	public List<Sequence> moveSelection(int diff) {
		List<Sequence> previousState = sequences.moveSelectionIfGapIsPresent(diff);
		if(previousState.size()> 0){
			fireSequencesChanged();
		}
		return previousState;
	}
	public boolean isMoveSelectionLeftPossible() {
		return sequences.isGapPresentLeftOfSelection();
	}
	public boolean isMoveSelectionRightPossible() {
		return sequences.isGapPresentRightOfSelection();
	}
	
	public List<Sequence> deleteGapMoveLeft() {
		List<Sequence> previousState = sequences.deleteGapMoveLeft();
		if(previousState.size()> 0){
			sequences.rightPadWithGapUntilEqualLength();
			fireSequencesChanged();
		}
		return previousState;
	}

	public List<Sequence> insertGapLeftOfSelectionMoveRight() {
		List<Sequence> previousState = sequences.insertGapLeftOfSelectedBase();
		if(previousState.size()> 0){
			sequences.rightPadWithGapUntilEqualLength();
			fireSequencesChanged();
		}
		return previousState;
	}
	
	public List<Sequence> insertGapRightOfSelectionMoveLeft() {
		List<Sequence> previousState = sequences.insertGapRightOfSelectedBase();
		if(previousState.size()> 0){
			sequences.rightPadWithGapUntilEqualLength();
			fireSequencesChanged();
		}
		return previousState;
	}

	public boolean rightPadSequencesWithGapUntilEqualLength(){	
		boolean wasPadded = sequences.rightPadWithGapUntilEqualLength();
		if(wasPadded){
			fireSequencesChanged();
		}
		return wasPadded;
		
	}
	
	public boolean leftPadSequencesWithGapUntilEqualLength() {
		boolean wasPadded = sequences.leftPadWithGapUntilEqualLength();
		if(wasPadded){
			fireSequencesChanged();
		}
		return wasPadded;
	}

	/*
	 * 
	 * End Aligner section
	 * 
	 */

	public long getSelectedBasesCount(){
		return sequences.getSelectionSize();
	}

	public Rectangle getSelectionAsMinRect(){
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;

		int seqIndex = 0;
		for(int n = 0; n < sequences.getSize(); n++){
			int[] selection = sequences.get(n).getSelectedPositions();
			if(selection.length > 0){
				minY = Math.min(minY, n);
				maxY = Math.max(maxY, n);
				minX = Math.min(minX, selection[0]);
				maxX = Math.max(minX, selection[selection.length - 1]);
			}
		}
		return new Rectangle(minX, minY, maxX - minX, maxY - minY);

	}


	public void addSelectionToExcludes() {
		for(Sequence sequence : sequences){
			int[] selection = sequence.getSelectedPositions();
			if(selection != null){
				for(int n = 0; n < selection.length; n++){
					this.alignmentMeta.excludePosition(selection[n]);
				}
			}
		}
		fireAlignmentMetaChanged();
	}

	public void removeSelectionFromExcludes() {
		for(Sequence sequence : sequences){
			int[] selection = sequence.getSelectedPositions();
			if(selection != null){
				for(int n = 0; n < selection.length; n++){
					this.alignmentMeta.getExcludes().getPositionsBooleanArray()[selection[n]] = false;
				}
			}
		}
		fireAlignmentMetaChanged();
	}

	public void setSelectionAsCoding(int startOffset) {
		for(Sequence sequence : sequences){
			int[] selection = sequence.getSelectedPositions();
			if(selection != null){
				for(int n = 0; n < selection.length; n++){
					int posVal = ((n + startOffset) % 3) + 1;
					this.alignmentMeta.getCodonPositions().setPosition(selection[n], posVal);
				}
			}
		}	
		this.alignmentMeta.getCodonPositions().fireUpdated();
		fireAlignmentMetaChanged();
	}

	public void setSelectionAsNonCoding() {
		for(Sequence sequence : sequences){
			int[] selection = sequence.getSelectedPositions();
			if(selection != null){
				for(int n = 0; n < selection.length; n++){
					this.alignmentMeta.getCodonPositions().setPosition(selection[n], 0);
				}
			}
		}
		this.alignmentMeta.getCodonPositions().fireUpdated();
		fireAlignmentMetaChanged();
	}

	public void complementAlignment() {	
		sequences.complement();
		fireSequencesChanged();
	}

	public boolean isEditedAfterLastSave() {
		return isEditedAfterLastSave;
	}

	public void setEditedAfterLastSave(boolean isEditedAfterLastSave) {
		this.isEditedAfterLastSave = isEditedAfterLastSave;
	}


	public boolean isAllCharactersValid() {
		return getFirstInvalidCharacter().length() == 0;
	}


	// TODO should be different depending on alignment class nuc or protein
	public String getFirstInvalidCharacter() {
		String testChars = "?ÅÄÖ*";
		String invalidCharsInAlignment = "";
		for(int n = 0; n < testChars.length(); n++){
			char testChar = testChars.charAt(n);
			for(Sequence seq: getSequences()){
				if(seq.contains(testChar)){
					invalidCharsInAlignment += testChar;
					break; // breaking out of this is loop no reason to check other seq
				}
			}
		}
		return invalidCharsInAlignment;
	}


	public void setAlignmentFormat(FileFormat fileFormat) {
		this.fileFormat = fileFormat;
	}

	public FileFormat getFileFormat() {
		return fileFormat;
	}

	public int getLongestSequenceName() {
		return sequences.getLongestSequenceName();
	}

	public int getCodonPosAt(int x) {
		return alignmentMeta.getCodonPosAt(x);
	}

	public void setReadingFrame(int readingFrame){
		if(readingFrame > 0 && readingFrame <=3){
			this.readingFrame = readingFrame;
			alignmentMeta.setReadingFrame(readingFrame);	
		}
	}

	public boolean isFullCodonStartingAt(int x) {
		return alignmentMeta.isFullCodonStartingAt(x);
	}

	public boolean isExcluded(int x) {
		return alignmentMeta.isExcluded(x);
	}

	public AlignmentMeta getAlignentMetaCopy(){
		return alignmentMeta.getCopy();
	}

	public void setAlignentMeta(AlignmentMeta aliMeta) {
		this.alignmentMeta = aliMeta;
		fireAlignmentMetaChanged();
	}

	public AlignmentMeta getAlignentMeta() {
		return alignmentMeta;
	}

	public ArrayList<Integer> getAllCodonPositions(int i, boolean removeExcluded) {		
		return alignmentMeta.getAllCodonPositions(i, removeExcluded);
	}

	/*
	 * 
	 * This one is just dumping result to stdout
	 * 
	 */
	public void findDuplicates() {
		StringBuilder dupeMessage = new StringBuilder("duplicates in alignment: " + alignmentFile.getName() + 
				                                      LF + "time: " + SimpleDateFormat.getDateTimeInstance().format(new Date()) + LF);
		String message = sequences.findDuplicates();
		dupeMessage.append(message);
		
		try {
			FileUtils.writeStringToFile(new File(alignmentFile.getParentFile(), "duplicates.log"), dupeMessage.toString() );
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/*
	 * 
	 * This one is just dumpting to stdout
	 * 
	 */
	public void getStats() {
		
			// TODO might not work if no charset
			for(CharSet charSet: alignmentMeta.getCharsets()){
				
				// Start with a prototype as consensus
				int consensusVal[] = new int[sequences.getLongestSequenceLength()];
				int missingCount = 0;
				int nonemptySeqCount = 0;
				
				for(Sequence seq: sequences){
					boolean sequenceIsAllEmpty = true;
					int sequenceMissingCount = 0;
					for(int n = 0; n <consensusVal.length; n++){
						
						if(charSet.isPositionIncluded(n)){
							
							int baseVal = NucleotideUtilities.baseValFromBase(seq.getBaseAtPos(n));
							// Create consensus by bitwise OR of the bases in the same column
							// Skip if n or unknown
							if(baseVal == NucleotideUtilities.N || baseVal == NucleotideUtilities.UNKNOWN || baseVal == NucleotideUtilities.GAP){
								sequenceMissingCount ++;
							}else{
								consensusVal[n] = consensusVal[n] | baseVal;
								sequenceIsAllEmpty = false;
							}
							
						}
					}
					if(! sequenceIsAllEmpty){
						nonemptySeqCount ++;
						missingCount += sequenceMissingCount;
					}
				}
				
				// Create consensus from baseValues
				StringBuilder consensusBuilder = new StringBuilder();
				int invaribleCount = 0;
				int varibleCount = 0;
				for(int n = 0; n < consensusVal.length; n++){
					if(charSet.isPositionIncluded(n)){
						consensusBuilder.append(NucleotideUtilities.charFromBaseVal(consensusVal[n]));
						if(consensusVal[n] == NucleotideUtilities.A || consensusVal[n] == NucleotideUtilities.C || consensusVal[n] == NucleotideUtilities.G || consensusVal[n] == NucleotideUtilities.T){
							invaribleCount ++;
						}
						else{
							varibleCount ++;
						}
					}
				}
				String consensus = consensusBuilder.toString();	
				
				logger.info(charSet.getName());
				logger.info("consensus" + consensus);
				logger.info("invaribleCount" + invaribleCount);
				logger.info("variableCount" + varibleCount);
				logger.info("totalCount" + (invaribleCount + varibleCount));
				logger.info("missingCount" + missingCount);
				logger.info("nonemptySeqCount" + nonemptySeqCount);
				double totalBases = (invaribleCount + varibleCount) * nonemptySeqCount;
				double missingPercent = missingCount/totalBases;
				logger.info("missingPercent" + missingPercent);
			}
	}


	public ArrayList<Sequence> deleteEmptySequences() {
		ArrayList<Sequence> deleted = sequences.deleteEmptySequences();
		if(deleted.size() > 0){
			fireSequencesRemoved();
		}
		return deleted;
	}

	public void deleteSequence(Sequence sequence) {
		sequences.deleteSequence(sequence);
		fireSequencesRemoved();
	}

	public int getFirstSelectedSequenceIndex() {
		int selectedIndex = -1;
		int n = 0;
		for(Sequence seq: sequences){
			if(seq.hasSelection()){
				selectedIndex = n;
				break;
			}
			n++;
		}
		return selectedIndex;
	}

	public void selectSequencesWithIndex(int[] selectedIndex) {
		sequences.selectSequencesWithIndex(selectedIndex);
		fireSelectionChanged();
	}

	public GeneticCode getGeneticCode() {
		return this.geneticCode;
	}
	
	public void setGeneticCode(GeneticCode genCode) {
		this.geneticCode = genCode;
		fireAlignmentMetaChanged();
	}

	public boolean isUndoable() {
		boolean isUndoable = true;
		if(this.getSequences() != null){
			if(this.getSequences() instanceof FileSequenceListModel){
				isUndoable = false;
			}
		}
		return isUndoable;
	}

	public boolean isNucleotideAlignment() {
		return (sequences.getSequenceType() == SequenceUtils.TYPE_NUCLEIC_ACID);
	}
	
	public boolean isAAAlignment() {
		return (sequences.getSequenceType() == SequenceUtils.TYPE_AMINO_ACID);
	}

	public void expandSelectionDown() {
		sequences.expandSelectionDown();
		fireSelectionChanged();
	}

	public boolean replaceSelectedWithChar(char newChar) {
		boolean wasReplaced = sequences.replaceSelectedWithChar(newChar);
		if(wasReplaced){
			fireSequencesChanged();
		}
		return wasReplaced;
		
	}

	public void selectEverythingWithinGaps(Point point) {
		sequences.selectEverythingWithinGaps(point);
		fireSelectionChanged();
	}

	public void realignNucleotidesUseThisAAAlignmentAsTemplate(Alignment realignment) {
		sequences.realignNucleotidesUseTheseAASequenceAsTemplate(realignment.getSequences(), alignmentMeta.getCodonPositions(), geneticCode);
		fireSequencesChanged();
	}

	public void deleteAllGaps() {
		sequences.deleteAllGaps();
	}

	public void padAndTrimSequences(){
		boolean wasPadded = sequences.rightPadWithGapUntilEqualLength();
		boolean wasTrimed = sequences.rightTrimSequencesRemoveGapsUntilEqualLength();
		if(wasPadded || wasTrimed){
			fireSequencesChanged();
		}
	}
	
	public void trimSequences(){
		boolean wasTrimed = sequences.rightTrimSequencesRemoveGapsUntilEqualLength();
		if( wasTrimed){
			fireSequencesChanged();
		}
	}

	public void selectAll(){
		sequences.selectAll();
		fireSelectionChanged();
	}

	public boolean isPositionValid(int x, int y) {
		return sequences.isPositionValid(x,y);
	}

	public boolean getSelectionAt(int x, int y) {
		return sequences.isBaseSelected(x, y);
	}

	public void setAllHorizontalSelectionAt(int y, boolean selection) {
		if(selection == true){
			sequences.get(y).selectAllBases();
		}else{
			sequences.get(y).clearAllSelection();
		}
		fireSelectionChanged();
	}

	public void copySelectionFromSequenceTo(int indexFrom, int indexTo) {
		sequences.copySelectionFromInto(indexFrom,indexTo);
		fireSelectionChanged();
		
	}

	public void setColumnSelection(int columnIndex, boolean selected) {
		sequences.selectColumn(columnIndex, selected);
		fireSelectionChanged();
	}

	public void copySelectionFromPosX1toX2(int x1, int x2) {
		sequences.copySelectionFromPosX1toX2(x1, x2);
		fireSelectionChanged();
		
	}

	public void setEditMode(boolean editMode) {
		this.editMode = editMode;
	}
	
	public boolean isEditMode() {
		return editMode;
	}

	public Point getFirstSelectedPosition() {
		return sequences.getFirstSelectedPos();
	}

	public void sortSequencesByName() {
		sequences.sortSequencesByName();
		fireSequenceOrderChanged();
	}

	public AliHistogram getHistogram(){
		if(showTranslationOnePos){
			return getTranslatedHistogram();
		}
		
		// TODO this cached histogram should be pushed into sequences
		if(cachedHistogram == null){
			cachedHistogram = sequences.getHistogram();	
		}
		return cachedHistogram;
	}

	private AliHistogram getTranslatedHistogram() {
		if(translatedHistogram == null){
			translatedHistogram = sequences.getTranslatedHistogram(new AATranslator(getAlignentMeta().getCodonPositions(),getGeneticCode()));	
		}
		return translatedHistogram;
	}

	public boolean hasSelection() {
		return sequences.hasSelection();
	}

	public void replaceSelectedCharactersWithThis(Alignment realignment) {
		List<Sequence> affected = sequences.replaceSelectedCharactersWithThis(realignment.getSequences());
		if(affected.size() > 0){
			fireSequencesChanged();
		}
	}

	public void saveSelectedSequencesAsFastaFile(File outFile, boolean useIDAsName) throws IOException {
		BufferedWriter buffWriter = new BufferedWriter(new FileWriter(outFile));
		sequences.writeSelectedSequencesAsFasta(buffWriter, useIDAsName);
		buffWriter.flush();
		buffWriter.close();
	}
	
	public void saveUnSelectedSequencesAsFastaFile(File outFile, boolean useIDAsName) throws IOException {
		BufferedWriter buffWriter = new BufferedWriter(new FileWriter(outFile));
		sequences.writeUnSelectedSequencesAsFasta(buffWriter, useIDAsName);
		buffWriter.flush();
		buffWriter.close();
	}

	public boolean isEditable() {
		boolean isEditable = false;
		if(sequences != null){
			isEditable = sequences.isEditable();
		}
		if(showTranslationOnePos){
			isEditable = false;
		}
		return isEditable;
	}
	
	public boolean isSelectable(){
		return isSelectable;
	}

	public long getSelectionSize() {
		return sequences.getSelectionSize();
	}

	public void fileSequenceContentsChanged() {
		logger.info("fileSequenceContChanged");
		if(alignmentMeta.getCodonPositions().getLength() == 0){
			alignmentMeta = new AlignmentMeta(sequences.getLongestSequenceLength());
		}
	}

	public void setTranslationOnePos(boolean showTranslationOnePos) {
		this.showTranslationOnePos = showTranslationOnePos;
		this.isSelectable = ! showTranslationOnePos;
	}

	public boolean hasFullySelectedSequences() {
		return sequences.hasFullySelectedSequences();
	}

	public void sortSequencesByThisModel(SequenceListModel prevSeqOrder) {
		sequences.sortSequencesByThisModel(prevSeqOrder);
	}

	public int getSelectedColumnCount() {
		return sequences.getSelectedColumnCount();
	}

	public int getSelectedSequencesCount() {
		return sequences.getSelectedSequencesCount();
	}

	public String getFirstSelectedSequenceName() {
		return sequences.getFirstSelectedName();
	}

	public void setFirstSelectedSequenceName(String newName) {
		sequences.setFirstSelectedName(newName);
	}

	public void selectAll(CharSet aCharSet) {
		sequences.clearSelection();
		for(int n = 0; n < sequences.getLongestSequenceLength(); n++){
			if(aCharSet.isPositionIncluded(n)){
				sequences.selectColumn(n, true);
			}else{
				// nothing to do - everything was deselected in beginning
			}
		}
		fireSelectionChanged();
	}
}
