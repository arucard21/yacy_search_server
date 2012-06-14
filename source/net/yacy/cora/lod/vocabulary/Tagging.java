/**
 *  Vocabulary
 *  Copyright 2012 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  first published 07.01.2012 on http://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.cora.lod.vocabulary;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import net.yacy.cora.storage.Files;
import net.yacy.document.WordCache.Dictionary;
import net.yacy.document.geolocalization.Locations;

public class Tagging {

    public final static String DEFAULT_NAMESPACE= "http://yacy.net/autotagging#";
    public final static String DEFAULT_PREFIX = "tags";

    private final String navigatorName;
    private final Map<String, String> synonym2term;
    private final Map<String, String> term2synonym;
    private final Map<String, Set<String>> synonym2synonyms;
    private File propFile;

    private String predicate, namespace, objectspace;

    public Tagging(String name) {
        this.navigatorName = name;
        this.synonym2term = new ConcurrentHashMap<String, String>();
        this.term2synonym = new ConcurrentHashMap<String, String>();
        this.synonym2synonyms = new ConcurrentHashMap<String, Set<String>>();
        this.namespace = DEFAULT_NAMESPACE;
        this.predicate = this.namespace + name;
        this.objectspace = null;
        this.propFile = null;
    }

   public Tagging(String name, File propFile) throws IOException {
        this(name);
        this.propFile = propFile;
        init();
    }

   /**
    * initialize a new Tagging file with a given table and objectspace url stub
    * @param name
    * @param propFile
    * @param objectspace
    * @param table
    * @throws IOException
    */
   public Tagging(String name, File propFile, String objectspace, Map<String,String> table) throws IOException {
       this(name);
       this.propFile = propFile;
       this.objectspace = objectspace;
       BufferedWriter w = new BufferedWriter(new FileWriter(propFile));
       w.write("#objectspace:" + objectspace + "\n");
       for (Map.Entry<String, String> e: table.entrySet()) {
           w.write(e.getKey() + (e.getValue() == null || e.getValue().length() == 0 ? "" : ":" + e.getValue()) + "\n");
       }
       w.close();
       init();
   }

    public void updateTerm(String term, String[] synonyms) {

    }

    private File tmpFile() {
        if (this.propFile == null) return null;
        return new File(this.propFile.getAbsolutePath() + ".tmp");
    }

    public void put(String term, String synonyms) throws IOException {
        if (this.propFile == null) return;
        File tmp = tmpFile();
        BufferedWriter w = new BufferedWriter(new FileWriter(tmp));
        BlockingQueue<String> list = Files.concurentLineReader(this.propFile, 1000);
        if (this.namespace != null && !this.namespace.equals(DEFAULT_NAMESPACE)) w.write("#namespace:" + this.namespace + "\n");
        if (this.objectspace != null && this.objectspace.length() > 0) w.write("#objectspace:" + this.objectspace + "\n");
        String line;
        boolean written = false;
        try {
            vocloop: while ((line = list.take()) != Files.POISON_LINE) {
                String[] pl = parseLine(line);
                if (pl == null) {
                    continue vocloop;
                }
                if (pl[0].equals(term)) {
                    w.write(term + (synonyms == null || synonyms.length() == 0 ? "" : ":" + synonyms) + "\n");
                    written = true;
                } else {
                    w.write(pl[0] + (pl[1] == null ? "" : ":" + pl[1]) + "\n");
                }
            }
            if (!written) {
                w.write(term + (synonyms == null || synonyms.length() == 0 ? "" : ":" + synonyms) + "\n");
            }
        } catch (InterruptedException e) {
        }
        w.close();
        this.propFile.delete();
        tmp.renameTo(this.propFile);
        init();
    }

    public void delete(String term) throws IOException {
        if (this.propFile == null) return;
        File tmp = tmpFile();
        BufferedWriter w = new BufferedWriter(new FileWriter(tmp));
        BlockingQueue<String> list = Files.concurentLineReader(this.propFile, 1000);
        if (this.namespace != null && !this.namespace.equals(DEFAULT_NAMESPACE)) w.write("#namespace:" + this.namespace + "\n");
        if (this.objectspace != null && this.objectspace.length() > 0) w.write("#objectspace:" + this.objectspace + "\n");
        String line;
        try {
            vocloop: while ((line = list.take()) != Files.POISON_LINE) {
                String[] pl = parseLine(line);
                if (pl == null) {
                    continue vocloop;
                }
                if (pl[0].equals(term)) {
                    continue vocloop;
                } else {
                    w.write(pl[0] + (pl[1] == null ? "" : ":" + pl[1]) + "\n");
                }
            }
        } catch (InterruptedException e) {
        }
        w.close();
        this.propFile.delete();
        tmp.renameTo(this.propFile);
        init();
    }

    public void clear() throws IOException {
        if (this.propFile == null) return;
        File tmp = tmpFile();
        BufferedWriter w = new BufferedWriter(new FileWriter(tmp));
        if (this.namespace != null && !this.namespace.equals(DEFAULT_NAMESPACE)) w.write("#namespace:" + this.namespace + "\n");
        if (this.objectspace != null && this.objectspace.length() > 0) w.write("#objectspace:" + this.objectspace + "\n");
        w.close();
        this.propFile.delete();
        tmp.renameTo(this.propFile);
        init();
    }

    public void setObjectspace(String os) throws IOException {
        if (this.propFile == null) return;
        if (os == null || (this.objectspace != null && this.objectspace.equals(os))) return;
        this.objectspace = os;
        File tmp = tmpFile();
        BufferedWriter w = new BufferedWriter(new FileWriter(tmp));
        BlockingQueue<String> list = Files.concurentLineReader(this.propFile, 1000);
        if (this.namespace != null && !this.namespace.equals(DEFAULT_NAMESPACE)) w.write("#namespace:" + this.namespace + "\n");
        if (this.objectspace != null && this.objectspace.length() > 0) w.write("#objectspace:" + this.objectspace + "\n");
        String line;
        try {
            vocloop: while ((line = list.take()) != Files.POISON_LINE) {
                String[] pl = parseLine(line);
                if (pl == null) {
                    continue vocloop;
                }
                w.write(pl[0] + (pl[1] == null ? "" : ":" + pl[1]) + "\n");
            }
        } catch (InterruptedException e) {
        }
        w.close();
        this.propFile.delete();
        tmp.renameTo(this.propFile);
        init();
    }

    public Map<String, Set<String>> reconstructionSets() {
        Map<String, Set<String>> r = new TreeMap<String, Set<String>>();
        for (Map.Entry<String, String> e: this.term2synonym.entrySet()) {
            Set<String> s = r.get(e.getKey());
            if (s == null) {
                s = new TreeSet<String>();
                r.put(e.getKey(), s);
            }
            if (e.getValue() != null && e.getValue().length() != 0) s.add(e.getValue());
        }
        for (Map.Entry<String, String> e: this.synonym2term.entrySet()) {
            Set<String> s = r.get(e.getValue());
            if (s == null) {
                s = new TreeSet<String>();
                r.put(e.getValue(), s);
            }
            s.add(e.getKey());
        }
        return r;
    }

    public Map<String, String> reconstructionLists() {
        Map<String, Set<String>> r = reconstructionSets();
        Map<String, String> map = new TreeMap<String, String>();
        for (Map.Entry<String, Set<String>> e: r.entrySet()) {
            StringBuilder sb = new StringBuilder(e.getValue().size() * 10);
            for (String s: e.getValue()) sb.append(',').append(s);
            map.put(e.getKey(), sb.substring(1));
        }
        return map;
    }

    public Map<String, String> list() {
        if (this.propFile == null) {
            // create a virtual map for automatically generated vocabularies
            return reconstructionLists();
        }
        Map<String, String> map = new LinkedHashMap<String, String>();
        BlockingQueue<String> list;
        try {
            list=Files.concurentLineReader(this.propFile, 1000);
        } catch (IOException e1) {
            return map;
        }
        String line;
        try {
            vocloop: while ((line = list.take()) != Files.POISON_LINE) {
                String[] pl = parseLine(line);
                if (pl == null) {
                    continue vocloop;
                }
                map.put(pl[0], pl[1] == null ? "" : pl[1]);
            }
        } catch (InterruptedException e) {
        }
        return map;
    }

    private final static String[] parseLine(String line) {
        line = line.trim();
        int p = line.indexOf('#');
        if (p >= 0) {
            line = line.substring(0, p).trim();
        }
        if (line.length() == 0) {
            return null;
        }
        p = line.indexOf(':');
        if (p < 0) {
            p = line.indexOf('=');
        }
        if (p < 0) {
            p = line.indexOf('\t');
        }
        if (p < 0) {
            return new String[]{line, null};
        }
        return new String[]{line.substring(0, p), line.substring(p + 1)};
    }

    public void init() throws IOException {
        if (this.propFile == null) return;
        this.synonym2term.clear();
        this.term2synonym.clear();
        this.synonym2synonyms.clear();
        this.namespace = DEFAULT_NAMESPACE;
        this.predicate = this.namespace + this.navigatorName;
        this.objectspace = null;

        BlockingQueue<String> list = Files.concurentLineReader(this.propFile, 1000);
        String term, v;
        String[] tags;
        int p;
        String line;
        try {
        	vocloop: while ((line = list.take()) != Files.POISON_LINE) {
			    line = line.trim();
			    p = line.indexOf('#');
			    if (p >= 0) {
			        String comment = line.substring(p + 1).trim();
                    if (comment.startsWith("namespace:")) {
                        this.namespace = comment.substring(10).trim();
                        if (!this.namespace.endsWith("/") && !this.namespace.endsWith("#") && this.namespace.length() > 0) this.namespace += "#";
                        this.predicate = this.namespace + this.navigatorName;
                    }
                    if (comment.startsWith("objectspace:")) {
                        this.objectspace = comment.substring(12).trim();
                        if (!this.objectspace.endsWith("/") && !this.objectspace.endsWith("#") && this.objectspace.length() > 0) this.objectspace += "#";
                    }
			    	line = line.substring(0, p).trim();
			    }
			    String[] pl = parseLine(line);
			    if (pl == null) {
			        continue vocloop;
			    }
			    if (pl[1] == null) {
			        term = normalizeKey(pl[0]);
			        v = normalizeWord(pl[0]);
			        this.synonym2term.put(v, term);
			        this.term2synonym.put(term, v);
			        continue vocloop;
			    }
			    term = normalizeKey(pl[0]);
			    v = pl[1];
			    tags = v.split(",");
			    Set<String> synonyms = new HashSet<String>();
			    synonyms.add(term);
			    tagloop: for (String synonym: tags) {
			        if (synonym.length() == 0) continue tagloop;
				    synonyms.add(synonym);
			    	synonym = normalizeWord(synonym);
			        if (synonym.length() == 0) continue tagloop;
				    synonyms.add(synonym);
			        this.synonym2term.put(synonym, term);
			        this.term2synonym.put(term, synonym);
			    }
			    String synonym = normalizeWord(term);
			    this.synonym2term.put(synonym, term);
			    this.term2synonym.put(term, synonym);
			    synonyms.add(synonym);
			    for (String s: synonyms) {
			    	this.synonym2synonyms.put(s, synonyms);
			    }
			}
		} catch (InterruptedException e) {
		}
    }

    public Tagging(String name, Locations localization) {
        this(name);
        Set<String> locNames = localization.locationNames();
        for (String loc: locNames) {
            this.synonym2term.put(loc.toLowerCase(), loc);
            this.term2synonym.put(loc, loc.toLowerCase());
        }
    }

    public Tagging(String name, Dictionary dictionary) {
        this(name);
        Set<StringBuilder> words = dictionary.getWords();
        String s;
        for (StringBuilder word: words) {
            s = word.toString();
            this.synonym2term.put(s.toLowerCase(), s);
            this.term2synonym.put(s, s.toLowerCase());
        }
    }

    /**
     * get the predicate name which already contains the prefix url stub
     * @return
     */
    public String getPredicate() {
        return this.predicate;
    }

    public String getNamespace() {
        return this.namespace;
    }

    public String getObjectspace() {
        return this.objectspace;
    }

    private final String normalizeKey(String k) {
        k = k.trim();
        k = k.replaceAll(" \\+", ", "); // remove symbols that are bad in a query attribute
        k = k.replaceAll(" /", ", ");
        k = k.replaceAll("\\+", ",");
        k = k.replaceAll("/", ",");
        k = k.replaceAll("  ", " ");
        return k;
    }

    /**
     * get the name of the navigator; this is part of the RDF predicate name (see: getPredicate())
     * @return
     */
    public String getName() {
        return this.navigatorName;
    }

    public File getFile() {
        return this.propFile;
    }

    public Metatag getMetatagFromSynonym(char prefix, final String word) {
        String printname = this.synonym2term.get(word);
        if (printname == null) return null;
        return new Metatag(prefix, printname);
    }

    public Metatag getMetatagFromTerm(char prefix, final String word) {
        return new Metatag(prefix, word);
    }

    public Set<String> getSynonyms(String term) {
    	return this.synonym2synonyms.get(term);
    }

    public Set<String> tags() {
        return this.synonym2term.keySet();
    }

    @Override
    public boolean equals(Object m) {
        Tagging m0 = (Tagging) m;
        return this.navigatorName.equals(m0.navigatorName);
    }

    @Override
    public int hashCode() {
        return this.navigatorName.hashCode();
    }

    @Override
    public String toString() {
        return this.term2synonym.toString();
    }

    private final static Pattern PATTERN_AE = Pattern.compile("\u00E4"); // german umlaute hack for better matching
    private final static Pattern PATTERN_OE = Pattern.compile("\u00F6");
    private final static Pattern PATTERN_UE = Pattern.compile("\u00FC");
    private final static Pattern PATTERN_SZ = Pattern.compile("\u00DF");

    public static final String normalizeWord(String word) {
        word = word.trim().toLowerCase();
        word = PATTERN_AE.matcher(word).replaceAll("ae");
        word = PATTERN_OE.matcher(word).replaceAll("oe");
        word = PATTERN_UE.matcher(word).replaceAll("ue");
        word = PATTERN_SZ.matcher(word).replaceAll("ss");
        return word;
    }

	public class Metatag {
	    private final String object;
	    private final char prefix;
	    public Metatag(char prefix, String object) {
	    	this.prefix = prefix;
	        this.object = object;
	    }

	    public String getVocabularyName() {
	        return Tagging.this.navigatorName;
	    }

        public String getPredicate() {
            return Tagging.this.predicate;
        }

        public String getObject() {
            return this.object;
        }

	    @Override
	    public String toString() {
	        return this.prefix + Tagging.this.navigatorName + ":" + encodePrintname(this.object);
	    }

	    @Override
	    public boolean equals(Object m) {
	        Metatag m0 = (Metatag) m;
	        return Tagging.this.navigatorName.equals(m0.getVocabularyName()) && this.object.equals(m0.object);
	    }

	    @Override
	    public int hashCode() {
	        return Tagging.this.navigatorName.hashCode() + this.object.hashCode();
	    }
	}

    private final static Pattern PATTERN_UL = Pattern.compile("_");
    private final static Pattern PATTERN_SP = Pattern.compile(" ");

    public static final String encodePrintname(String printname) {
        return PATTERN_SP.matcher(printname).replaceAll("_");
    }

    public static final String decodeMaskname(String maskname) {
        return PATTERN_UL.matcher(maskname).replaceAll(" ");
    }

    public static String cleanTagFromAutotagging(char prefix, final String tagString) {
        if (tagString == null || tagString.length() == 0) return "";
        String[] tags = PATTERN_SP.split(tagString);
        StringBuilder sb = new StringBuilder(tagString.length());
        for (String tag : tags) {
            if (tag.length() > 0 && tag.charAt(0) != prefix) {
                sb.append(tag).append(' ');
            }
        }
        if (sb.length() == 0) return "";
        return sb.substring(0, sb.length() - 1);
    }

}