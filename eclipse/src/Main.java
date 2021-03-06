import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.PriorityQueue;

import Database.Song;
import Distance.Cosine;
import Feature.MFCC;
import SignalProcess.WaveIO;
import Tool.ObjectIO;
import Tool.Timer;

@SuppressWarnings("unused")
public class Main {

	private static final double MFCC_SIMILARITY_THRESHOLD = 0.985;
	private static final double MFCC_ACCEPTANCE_THRESHOLD = 0.993;
	private static final int FRAME_COUNT_ACCEPTANCE_THRESHOLD = 12; // Multiply by 0.75 secs for actual length.
	private static final double MFCC_FLATTEN_LOWER_THRESHOLD = 0.004; // The maximum percentage difference of MFCC score a matched sample can go compared to the best match
	private static final int NON_MATCH_FRAME_TOLERANCE = 3;

	private static final FilenameFilter WAV_EXTENSION_FILTER = new FilenameFilter() {
		@Override
		public boolean accept(File dir, String name) {
			if (name.toLowerCase().endsWith(".wav"))
				return true;
			return false;
		}
	};
	private static final FilenameFilter DB_EXTENSION_FILTER = new FilenameFilter() {
		@Override
		public boolean accept(File dir, String name) {
			if (name.toLowerCase().endsWith(".db"))
				return true;
			return false;
		}
	};

	public static void main(String[] args) throws FileNotFoundException, IOException, ClassNotFoundException {
		if(args.length != 2) {
			System.err.println("Usage: java -Xmx8000M -jar YouSicMatcher.jar pathToSongDatabase pathToWaveFile");
			System.exit(1);
		}

		//Notes for input: input should be 44100 Hz, Mono, 16-bit per sample. Remove metadata too.

		// ~3 second window with ~0.75 secs shift (~4.5 secs overlap)
		// Actual window = 131072/44100 = ~2.972 secs
		MFCC mfcc = new MFCC(131072, 131072/4*3);

		PriorityQueue<Result> results = match(mfcc, args[1], args[0]);
		printProductionOutput(results, 131072.0/4.0/44100.0);


//		ObjectIO.writeObject("C:/Users/Ian/Google Drive/Music/TestcaseVideo/tc1.db", generateSongObject(mfcc, "C:/Users/Ian/Google Drive/Music/TestcaseVideo/tc1.wav"));
//		ObjectIO.writeObject("C:/Users/Ian/Google Drive/Music/TestcaseVideo/tc2.db", generateSongObject(mfcc, "C:/Users/Ian/Google Drive/Music/TestcaseVideo/tc2.wav"));
//		ObjectIO.writeObject("C:/Users/Ian/Google Drive/Music/TestcaseVideo/tc3.db", generateSongObject(mfcc, "C:/Users/Ian/Google Drive/Music/TestcaseVideo/tc3.wav"));
//		ObjectIO.writeObject("C:/Users/Ian/Google Drive/Music/TestcaseVideo/tc4.db", generateSongObject(mfcc, "C:/Users/Ian/Google Drive/Music/TestcaseVideo/tc4.wav"));
//		ObjectIO.writeObject("C:/Users/Ian/Google Drive/Music/TestcaseVideo/tc5.db", generateSongObject(mfcc, "C:/Users/Ian/Google Drive/Music/TestcaseVideo/tc5.wav"));
//		System.out.println("Done");

		//createSongsDatabase(mfcc, "C:/Users/Ian/Google Drive/Music/wav", "C:/Users/Ian/Desktop/songs.db");
		//createSongsDatabase(mfcc, "C:/Users/Ian/Google Drive/Music/Testcase_wav", "C:/Users/Ian/Desktop/songs.db");
//		mergeSongsDatabase("C:/Users/Ian/Desktop/songs.db");
//		match(mfcc, "C:/Users/Ian/Google Drive/Music/TestcaseVideo/tc3.wav", "C:/Users/Ian/Desktop/songs2.db");
//		PriorityQueue<Result> results = match(mfcc, "C:/Users/Ian/Desktop/test3.wav", "C:/Users/Ian/Desktop/songs2.db");


	}

	private static void createSongsDatabase(MFCC mfcc, String folderPath, String databaseFilePath) throws FileNotFoundException, IOException {
		File files[] = new File(folderPath).listFiles(WAV_EXTENSION_FILTER);

		File individualFolder = new File(databaseFilePath + "-indiv/");
		individualFolder.mkdirs();

		for(int i = 0; i < files.length; i++) {
			Timer.gtime();
			System.out.println("Processing " + (i+1) + " out of " + files.length);

			Song song = generateSongObject(mfcc, files[i]);
			ObjectIO.writeObject(databaseFilePath + "-indiv/" + files[i].getName().replace(".wav", ".db"), song);

			System.out.println("Processed " + song);
		}
		System.out.println("Done.");
	}

	private static void mergeSongsDatabase(String databaseFilePath) throws FileNotFoundException, IOException, ClassNotFoundException {
		ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(databaseFilePath));

		File individualFolder = new File(databaseFilePath + "-indiv/");
		File files[] = individualFolder.listFiles(DB_EXTENSION_FILTER);

		for(int i = 0; i < files.length; i++) {
			System.out.println("Processing " + (i+1) + " out of " + files.length);

			Song song = (Song)ObjectIO.readObject(files[i]);
			oos.writeObject(song);

			System.out.println("Processed " + song);
		}
		oos.close();
		System.out.println("Done.");
	}

	private static PriorityQueue<Result> match(MFCC mfcc, String sampleFilePath, String databaseFilePath) throws FileNotFoundException, ClassNotFoundException, IOException {
		return match(mfcc, new File(sampleFilePath), databaseFilePath);
	}

	private static PriorityQueue<Result> match(MFCC mfcc, File sampleFilePath, String databaseFilePath) throws FileNotFoundException, IOException, ClassNotFoundException {
		System.err.println("Processing sample...");
		Timer.greset();

//		Song sample = (Song)ObjectIO.readObject("C:/Users/Ian/Google Drive/Music/TestcaseVideo/tc5.db");
//		double sampleMfcc[][] = sample.getMfcc();

		short[] sampleData = WaveIO.readWave(sampleFilePath);
		double sampleMfcc[][] = mfcc.process(sampleData);
		int sampleLength = sampleMfcc.length;

		System.err.println("Sample processed. Matching...");
		Timer.gtime();

		ObjectInputStream ois = new ObjectInputStream(new FileInputStream(databaseFilePath));
		PriorityQueue<Result> results = new PriorityQueue<Result>();

		while(true) {
			Song song;
			try {
				song = (Song)ois.readObject();
			} catch(EOFException e) {
				break;
			}

//			System.out.println(song);
//			if(!song.getArtist().equals("Steve Jablonsky")) continue;
//			else System.out.println("Steve Jablonsky");

			PriorityQueue<Result> songResults = new PriorityQueue<Result>();

			int songLength = song.getMfcc().length;

			int i, j, k;
			for(i = 0; i < songLength; i++) {
				for(k = 0; k < sampleLength; k++) { // sampleOffset
					double totalMfccScores = 0.0;

					for(j = k; j < sampleLength && i + j < songLength; j++) {
						double mfccSimilarity = Cosine.getDistance(song.getMfcc()[i + j], sampleMfcc[j]);
						if(mfccSimilarity < MFCC_SIMILARITY_THRESHOLD)
							break;

						totalMfccScores += mfccSimilarity;
					}

					int frameCount = j - k;

					if(frameCount >= FRAME_COUNT_ACCEPTANCE_THRESHOLD && totalMfccScores / frameCount >= MFCC_ACCEPTANCE_THRESHOLD) {
						songResults.add(new Result(totalMfccScores / frameCount, i, k, frameCount, song.toString()));
					}
				}
			}

//			System.out.println("PRE: " + songResults.size());
			flattenResults(songResults, songLength, sampleLength);
//			System.out.println("POST: " + songResults.size());
			results.addAll(songResults);
		}

		ois.close();

		removeMultipleHits(results, sampleLength);

//		System.out.println("Results count: " + results.size());
//		int printCount = 0;
//		while(!results.isEmpty() && printCount != 100) {
//			System.err.println(results.poll());
//			printCount++;
//		}

		for(Result i : results)
			System.err.println(i);
		System.err.println();

		// Reorder results by sample start time
		PriorityQueue<Result> tempResults = new PriorityQueue<>(Result.SAMPLE_START_TIME_COMPARATOR);
		tempResults.addAll(results);
		results = tempResults;

		System.err.println("Matching done.");
		Timer.gtime();

		return results;
	}

	private static void flattenResults(PriorityQueue<Result> results, int songLength, int sampleLength) {
		if(results.isEmpty()) return;

		String songName = results.peek().getSong();
		double lowerThreshold = results.peek().getMfcc() - MFCC_FLATTEN_LOWER_THRESHOLD;

		double mfccAverage = 0.0;
		for(Result i : results) {
			mfccAverage += i.getMfcc();
		}
//		System.err.println(songName);
//		System.err.println("Flatten: " + results.size());
//		System.err.println(mfccAverage / results.size());
//		System.err.println();

		double mfccScores[] = new double[sampleLength];
		while(!results.isEmpty()) {
			Result r = results.poll();
			if(r.getMfcc() < lowerThreshold)
				continue;
//			System.out.println(r);
			for(int i = 0; i < r.getLength(); i++) {
				if(mfccScores[r.getSampleStartPosition() + i] == 0.0) {
					mfccScores[r.getSampleStartPosition() + i] = r.getMfcc();
				}
			}
		}

//		System.out.println(songName);

		PriorityQueue<Result> resultsByTime = new PriorityQueue<Result>(Result.SAMPLE_START_TIME_COMPARATOR);

		double totalMfccScore = 0.0;
		int totalMfccCount = 0;
		int i;
		for(i = 0; i < mfccScores.length; i++) {
			if(mfccScores[i] == 0.0) {
				if(totalMfccCount != 0) {
					resultsByTime.add(new Result(totalMfccScore / totalMfccCount, -1, i - totalMfccCount, totalMfccCount, songName));
					totalMfccScore = 0.0;
					totalMfccCount = 0;
				}
			}
			else {
				totalMfccScore += mfccScores[i];
				totalMfccCount++;
			}
		}

		if(totalMfccCount != 0) {
			resultsByTime.add(new Result(totalMfccScore / totalMfccCount, -1, i - totalMfccCount, totalMfccCount, songName));
			totalMfccScore = 0.0;
			totalMfccCount = 0;
		}

		if(resultsByTime.isEmpty())
			return;

		Result currentResult = resultsByTime.poll();
		while(!resultsByTime.isEmpty()) {
			Result nextResult = resultsByTime.poll();
			if(nextResult.getSampleStartPosition() - currentResult.getSampleStartPosition() - currentResult.getLength() <= NON_MATCH_FRAME_TOLERANCE) {

				//new Result(mfcc, songStartPosition, sampleStartPosition, length, song)
				currentResult = new Result(
					currentResult.getMfcc() * currentResult.getLength() / (currentResult.getLength() + nextResult.getLength())
						+ nextResult.getMfcc() * nextResult.getLength() / (currentResult.getLength() + nextResult.getLength()),
					currentResult.getSongStartPosition(),
					currentResult.getSampleStartPosition(),
					currentResult.getLength() + nextResult.getLength() + (nextResult.getSampleStartPosition() - currentResult.getSampleStartPosition() - currentResult.getLength()),
					currentResult.getSong()
				);
			}
			else {
				results.add(currentResult);
				currentResult = nextResult;
			}
		}
		results.add(currentResult);
	}

	private static void removeMultipleHits(PriorityQueue<Result> results, int sampleLength) {
		if(results.isEmpty()) return;

		ArrayList<Result> acceptedResults = new ArrayList<Result>();

		boolean isOccupied[] = new boolean[sampleLength];
		while(!results.isEmpty()) {
			Result r = results.poll();

			int hitCount = 0;
			int firstHitPosition = -1;
			for(int i = 0; i < r.getLength(); i++) {
				if(!isOccupied[r.getSampleStartPosition() + i]) {
					if(hitCount == 0) {
						firstHitPosition = r.getSampleStartPosition() + i;
					}
					hitCount++;
					isOccupied[r.getSampleStartPosition() + i] = true;
				}
				else if(hitCount != 0) {
					break;
				}
			}

			if(hitCount >= FRAME_COUNT_ACCEPTANCE_THRESHOLD) {
				r.setSampleStartPosition(firstHitPosition);
				r.setLength(hitCount);
				acceptedResults.add(r);
			}
		}

		results.addAll(acceptedResults);
	}

	private static void printProductionOutput(PriorityQueue<Result> results, double sizeOfWindow) {
		while(!results.isEmpty()) {
			Result r = results.poll();

			long startSec = Math.round(r.getSampleStartPosition() * sizeOfWindow);
			long startMin = startSec / 60L;
			startSec %= 60L;

			long endSec = Math.round((r.getSampleStartPosition() + r.getLength()) * sizeOfWindow);
			long endMin = endSec / 60L;
			endSec %= 60L;

			System.out.println(startMin + " " + startSec + " " + endMin + " " + endSec + " " + r.getSong());
		}
	}

	private static Song generateSongObject(MFCC mfcc, String filename) {
		return generateSongObject(mfcc, new File(filename));
	}

	private static Song generateSongObject(MFCC mfcc, File file) {
		Song song;
		String[] artistAndTitle = file.getName().replace(".wav", "").split(" - ", 2);
		short[] data = WaveIO.readWave(file);
		if(artistAndTitle.length == 2) {
			song = new Song(
					artistAndTitle[0],
					artistAndTitle[1],
					mfcc.process(data),
					null
					);
		}
		else {
			song = new Song(
					null,
					file.getName().replace(".wav", ""),
					mfcc.process(data),
					null
					);
		}
		return song;
	}
}

