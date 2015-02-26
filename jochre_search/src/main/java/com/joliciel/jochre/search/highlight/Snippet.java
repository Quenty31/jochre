///////////////////////////////////////////////////////////////////////////////
//Copyright (C) 2014 Assaf Urieli
//
//This file is part of Jochre.
//
//Jochre is free software: you can redistribute it and/or modify
//it under the terms of the GNU Affero General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//Jochre is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU Affero General Public License for more details.
//
//You should have received a copy of the GNU Affero General Public License
//along with Jochre.  If not, see <http://www.gnu.org/licenses/>.
//////////////////////////////////////////////////////////////////////////////
package com.joliciel.jochre.search.highlight;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.joliciel.talismane.utils.LogUtils;

/**
 * A single snippet within a document, used for displaying the document.
 */
public class Snippet implements Comparable<Snippet> {
	private static final Log LOG = LogFactory.getLog(Snippet.class);
	private int docId;
	private String field;
	private int startOffset;
	private int endOffset;
	private boolean scoreCalculated = false;
	private double score;
	private List<HighlightTerm> highlightTerms = new ArrayList<HighlightTerm>();
	
	public Snippet(int docId, String field, int startOffset, int endOffset) {
		super();
		this.docId = docId;
		this.field = field;
		this.startOffset = startOffset;
		this.endOffset = endOffset;
	}
	
	public Snippet(String json) {
		try {
			Reader reader = new StringReader(json);
			JsonFactory jsonFactory = new JsonFactory(); // or, for data binding, org.codehaus.jackson.mapper.MappingJsonFactory 
			JsonParser jsonParser = jsonFactory.createJsonParser(reader); 
			jsonParser.nextToken();
			this.read(jsonParser);
 		} catch (IOException e) {
 			LOG.error(e);
 			throw new RuntimeException(e);
 		}
 	}
	
	public Snippet(JsonParser jsonParser) {
		this.read(jsonParser);
	}
	
	private void read(JsonParser jsonParser) {
		try {
 			if (jsonParser.getCurrentToken()!= JsonToken.START_OBJECT)
 				throw new RuntimeException("Expected START_OBJECT, but was " + jsonParser.getCurrentToken() + " at " + jsonParser.getCurrentLocation());
 			while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
				String fieldName = jsonParser.getCurrentName();
				
				if (fieldName.equals("docId")) {
					this.docId = jsonParser.nextIntValue(0);
				} else if (fieldName.equals("field")) {
					this.field = jsonParser.nextTextValue();
				} else if (fieldName.equals("start")) {
					this.startOffset = jsonParser.nextIntValue(0);
				} else if (fieldName.equals("end")) {
					this.endOffset = jsonParser.nextIntValue(0);
				} else if (fieldName.equals("score")) {
					jsonParser.nextValue();
					this.score = jsonParser.getDoubleValue();
					this.scoreCalculated = true;
				} else if (fieldName.equals("terms")) {
		 			if (jsonParser.nextToken() != JsonToken.START_ARRAY)
		 				throw new RuntimeException("Expected START_ARRAY, but was " + jsonParser.getCurrentToken() + " at " + jsonParser.getCurrentLocation());
		 			while (jsonParser.nextToken() != JsonToken.END_ARRAY) {
		 				if (jsonParser.getCurrentToken() != JsonToken.START_OBJECT)
		 	 				throw new RuntimeException("Expected START_OBJECT, but was " + jsonParser.getCurrentToken() + " at " + jsonParser.getCurrentLocation());
		 	 			int termDocId = docId;
		 	 			String termField = field;
		 				int termStart = 0;
		 	 			int termEnd = 0;
		 	 			int pageIndex = 0;
		 	 			int imageIndex = 0;
		 	 			double weight = 0.0;
		 				while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
			 				String termFieldName = jsonParser.getCurrentName();
			 				if (termFieldName.equals("docId")) {
			 					termDocId = jsonParser.nextIntValue(0);
			 				} else if (termFieldName.equals("field")) {
			 					termField = jsonParser.nextTextValue();
			 				} else if (termFieldName.equals("start")) {
			 					termStart = jsonParser.nextIntValue(0);
			 				} else if (termFieldName.equals("end")) {
			 					termEnd = jsonParser.nextIntValue(0);
			 				} else if (termFieldName.equals("pageIndex")) {
			 					pageIndex = jsonParser.nextIntValue(0);
			 				} else if (termFieldName.equals("imageIndex")) {
			 					imageIndex = jsonParser.nextIntValue(0);
			 				} else if (termFieldName.equals("weight")) {
								jsonParser.nextValue();
								weight = jsonParser.getDoubleValue();
							} else {
								throw new RuntimeException("Unexpected term field name: " + termFieldName + " at " + jsonParser.getCurrentLocation());
							}
			 			}
	 	 				HighlightTerm highlightTerm = new HighlightTerm(termDocId, termField, termStart, termEnd, imageIndex, pageIndex);
	 	 				highlightTerm.setWeight(weight);
	 	 				this.highlightTerms.add(highlightTerm);
		 			}
				} else {
					throw new RuntimeException("Unexpected field name: " + fieldName + " at " + jsonParser.getCurrentLocation());
				}
 			}
		} catch (JsonParseException e) {
 			LOG.error(e);
 			throw new RuntimeException(e);
 		} catch (IOException e) {
 			LOG.error(e);
 			throw new RuntimeException(e);
 		}
 	}
	
	public String toJson() {
		try {
			DecimalFormatSymbols enSymbols = new DecimalFormatSymbols(Locale.US);
			DecimalFormat df = new DecimalFormat("0.00", enSymbols);
			StringWriter writer = new StringWriter();
			JsonFactory jsonFactory = new JsonFactory();
			JsonGenerator jsonGen = jsonFactory.createGenerator(writer);
			this.toJson(jsonGen, df);
			writer.flush();
			String json = writer.toString();
			return json;
		} catch (IOException ioe) {
			LogUtils.logError(LOG, ioe);
			throw new RuntimeException(ioe);
		}
	}

	public void toJson(JsonGenerator jsonGen, DecimalFormat df) {
		try {
			jsonGen.writeStartObject();
			jsonGen.writeNumberField("docId", docId);
			jsonGen.writeStringField("field", this.getField());
			jsonGen.writeNumberField("start", this.getStartOffset());
			jsonGen.writeNumberField("end", this.getEndOffset());
			double roundedScore = df.parse(df.format(this.getScore())).doubleValue();
			jsonGen.writeNumberField("score", roundedScore);
			jsonGen.writeArrayFieldStart("terms");
			for (HighlightTerm term : this.getHighlightTerms()) {
				term.toJson(jsonGen, df);
			}
			jsonGen.writeEndArray(); // terms
	
			jsonGen.writeEndObject();
			jsonGen.flush();
		} catch (java.text.ParseException e) {
			LogUtils.logError(LOG, e);
			throw new RuntimeException(e);
		} catch (IOException ioe) {
			LogUtils.logError(LOG, ioe);
			throw new RuntimeException(ioe);
		}
	}
	
	public int getStartOffset() {
		return startOffset;
	}
	public int getEndOffset() {
		return endOffset;
	}
	
	public int getDocId() {
		return docId;
	}
	
	public String getField() {
		return field;
	}
	public double getScore() {
		if (!scoreCalculated) {
			score = 0;
			for (HighlightTerm term : this.highlightTerms) {
				score += term.getWeight();
			}
			scoreCalculated = true;
		}
		return score;
	}
	
	public boolean hasOverlap(Snippet otherSnippet) {
		return this.getDocId()==otherSnippet.getDocId() && this.getField().equals(otherSnippet.getField())
				&& ((this.startOffset<otherSnippet.getEndOffset() && this.endOffset>=otherSnippet.getStartOffset())
						|| (otherSnippet.getStartOffset()<this.endOffset && otherSnippet.getEndOffset()>=this.startOffset));
	}
	
	public void merge(Snippet otherSnippet) {
		if (this.startOffset<otherSnippet.getStartOffset() && this.endOffset<otherSnippet.getEndOffset()) {
			this.endOffset = otherSnippet.endOffset;
		} else if (this.startOffset>otherSnippet.getStartOffset() && this.endOffset>otherSnippet.getEndOffset()) {
			this.startOffset = otherSnippet.getStartOffset();
		}
		Set<HighlightTerm> newTerms = new TreeSet<HighlightTerm>(this.highlightTerms);
		newTerms.addAll(otherSnippet.getHighlightTerms());
		this.highlightTerms = new ArrayList<HighlightTerm>(newTerms);
		this.scoreCalculated = false;
	}
	
	@Override
	public int compareTo(Snippet o) {
		if (this==o)
			return 0;
		
		if (this.score!=o.getScore())
			return o.getScore() > this.score ? 1 : -1;
		
		if (this.getDocId()!=o.getDocId())
			return this.getDocId() - o.getDocId();
		
		if (!this.getField().equals(o.getField()))
			return this.getField().compareTo(o.getField());

		if (this.startOffset!=o.getStartOffset())
			return this.startOffset - o.getStartOffset();
		
		if (this.endOffset!=o.getEndOffset())
			return this.endOffset - o.getEndOffset();
		
		return o.getEndOffset() - this.endOffset;
	}
	
	/**
	 * The highlight terms contained in this snippet.
	 * @return
	 */
	public List<HighlightTerm> getHighlightTerms() {
		return highlightTerms;
	}
	public void setHighlightTerms(List<HighlightTerm> highlightTerms) {
		this.highlightTerms = highlightTerms;
	}

	public int getPageIndex() {
		if (this.highlightTerms.size()>0)
			return this.highlightTerms.get(0).getPageIndex();
		return 0;
	}
	
	
}