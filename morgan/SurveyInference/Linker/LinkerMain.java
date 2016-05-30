/*
 * Linker is a tool for generating network files from a structured data-sheet.
 * It generates network data in a format directly compatible with ORA (Netanomics)
 * It is different from the other tools (including ORA) because it allows you to use 
 * multiple attributes of a data-sheet to differentiate nodes.
 * Copyright 2016, Geoffrey P Morgan and Kelly Garbach
 * 
 * This tool is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License version 2 as published by the Free Software Foundation.
 * 
 * This tool is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Library General Public License for more details.
 * 
 * You should have received a copy of the GNU Library General Public
 * License along with this tool; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
 * Boston, MA 02110-1301, USA.
 * 
 * You can also read the license directly at: http://www.gnu.org/licenses/gpl-2.0.html
 *
 * You can contact the authors by e-mailing kgarbach@luc.edu
*/

package morgan.SurveyInference.Linker;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import javax.swing.JFileChooser;

/**
 * This script initializes the Linker.  It reads the initial configuration and data file
 * and then starts up the UI.
 * 
 * It also includes the DynetML file write methods.
 * 
 * @author gmorgan, kgarbach
 *
 */
public class LinkerMain {


	/**
	 * Collaborators create a definition for a NodeType
	 */
	static ArrayList<NodeDefinition> nodeDefinitions = new ArrayList<NodeDefinition>();
	/**
	 * The participant data is a whole set of hashmaps, one per participant
	 */
	static ArrayList<HashMap<String, String>> pData;
	/**
	 * The file which is being used to generate the 
	 */
	static File dataFile;
	static String lastFileLocation = ".";
	static String CONJOINER = "+++";
	
	/**
	 * All headers found in the data
	 */
	static ArrayList<String> headers = new ArrayList<String>();
	
	/**
	 * This indicates the data separation in the file, by default tab-delimited
	 */
	static String fileDelimiter = "\t";
	

	/**
	 * Read a data file and then start up the Linker UI
	 * @param args
	 */
	public static void main(String[] args) {
		// What we're doing in this tool is defining what is the criteria for a node entity in a network
		// For example, a person is defined by their:
		//   a) Name
		//   b) Name and Role
		//   c) Role, County

		try {
			setupNodeDefinitions();

			JFileChooser dataFileChooser = new JFileChooser(lastFileLocation);
			dataFileChooser.setDialogTitle("Select anonymized data-file to read:");
			dataFileChooser.setFileFilter(new TXTFileFilter());
			int returnVal = dataFileChooser.showOpenDialog(null);
			if(returnVal == JFileChooser.APPROVE_OPTION) {
				lastFileLocation = dataFileChooser.getSelectedFile().getParent();
				dataFile = dataFileChooser.getSelectedFile();

				pData = readDataFile(dataFile);
				new LinkerFrame("Linker: Identifying nodes to establish linkages", nodeDefinitions, headers);
			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}




	}

	/**
	 * We use the participant data to process all node definitions given, we assume
	 * that the first collaborator identified will be connected to all the other nodes.
	 * 
	 * @param definitions, a set of node-definitions to be used to identify nodes
	 * @param pData, the survey data
	 * @return a HashMap of unique-ids and their accompanying nodes
	 */
	static HashMap<String, IdentifiedNode> identifyUniqueNodes(ArrayList<NodeDefinition> definitions, ArrayList<HashMap<String, String>> pData) {

		HashMap<String, IdentifiedNode> uniqueNodes = new HashMap<String, IdentifiedNode>();

		for(HashMap<String, String> participant : pData) {

			ArrayList<IdentifiedNode> foundEntities = new ArrayList<IdentifiedNode>();
			for(NodeDefinition c : definitions) {			
				String node = "";

				for(String ID : c.identifyingCharacteristics) {
					if(participant.containsKey(ID) && !participant.get(ID).equals("")) {
						node += participant.get(ID) + CONJOINER;
					}
					else {
						node = "INVALID";
					}
				}

				if(!node.contains("INVALID") && !node.equals("")) {

					node = node.substring(0, node.length()-CONJOINER.length());

					if(c.hasDelimiter) {
						//System.out.println("This NodeType Definition allows delimiter.");

						String[] nodeElements = node.split(c.delimiter);
						for(String newNodeID : nodeElements) {
							//System.out.println("\tNew Node ID: " + newNodeID);
							identifyUniqueNode(c, newNodeID, participant, foundEntities, uniqueNodes);
						}

					}
					else {
						identifyUniqueNode(c, node, participant, foundEntities, uniqueNodes);
					}
				}
			}

			for(int i = 1; i < foundEntities.size(); ++i) {
				foundEntities.get(0).ties.add(foundEntities.get(i).id);
			}

		}


		return uniqueNodes;
	}

	/**
	 * We take a given node definition and use that to define the node and produce a node object
	 * @param definition, the definition we're instantiating
	 * @param uniqueNodeID, the unique id of the node we're going to instantiate
	 * @param participant, the participant data
	 * @param collaborators, a set of identified node objects within this participant's data
	 * @param uniqueNodes, the full set of all identified nodes
	 */
	static void identifyUniqueNode(NodeDefinition definition, String uniqueNodeID, HashMap<String, String> participant, ArrayList<IdentifiedNode> collaborators, HashMap<String, IdentifiedNode> uniqueNodes) {
		uniqueNodeID = uniqueNodeID.replaceAll("&", "+");
		
		if(!uniqueNodes.containsKey(uniqueNodeID)) {
			uniqueNodes.put(uniqueNodeID, new IdentifiedNode(uniqueNodeID, definition.type));
		}
		IdentifiedNode theEntity = uniqueNodes.get(uniqueNodeID);
		//theEntity.addCharacteristic("ID_From_Data", uniqueNodeID);

		for(String characteristic : definition.identifyingCharacteristics) {
			if(participant.containsKey(characteristic)) {
				theEntity.addCharacteristic(characteristic, participant.get(characteristic));
			}
		}

		for(String characteristic : definition.otherCharacteristics) {
			if(participant.containsKey(characteristic)) {
				theEntity.addCharacteristic(characteristic, participant.get(characteristic));
			}
		}

		uniqueNodes.remove(theEntity);
		uniqueNodes.put(uniqueNodeID, theEntity);
		collaborators.add(theEntity);
	}

	/**
	 * Write out the identified nodes and links to DynetML
	 * @param nodes, the instantiated nodes
	 * @param id, the id of the network file
	 * @param f, the file to write out
	 */
	static void writeDynetML(HashMap<String, IdentifiedNode> nodes, String id, File f) {
		try {
			HashMap<String, IdentifiedNode> rejiggeredNodes = new HashMap<String, IdentifiedNode>();
			for(IdentifiedNode c : nodes.values()) {
				rejiggeredNodes.put(c.id, c);
			}
			BufferedWriter writer = new BufferedWriter(new FileWriter(f));
			writeDynetMLHeader(writer, id);
			writeDynetMLNodes(writer, rejiggeredNodes);
			writeDynetMLEdges(writer, rejiggeredNodes);
			writeDynetMLFooter(writer);
			writer.flush();
			writer.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Write out the DynetML header
	 * @param writer, the BufferedWriter connected to the output file
	 * @param networkID, the id of the network
	 * @throws IOException, in case writing to the file fails
	 */
	static void writeDynetMLHeader(BufferedWriter writer, String networkID) throws IOException {
		writer.write("<?xml version=\"1.0\" standalone=\"yes\"?>\n<DynamicMetaNetwork id=\"" + networkID + "\">");
		writer.write("\n\t<MetaNetwork id=\"" + networkID + "\">");
	}

	/**
	 * Write out the DynetML footer
	 * @param writer, the BufferedWriter connected to the output file
	 * @throws IOException, in case writing to the file fails
	 */
	static void writeDynetMLFooter(BufferedWriter writer) throws IOException {
		// </MetaNetwork>
		// </DynamicMetaNetwork>
		writer.write("\n\t</MetaNetwork>");
		writer.write("\n</DynamicMetaNetwork>");
	}

	static void writeDynetMLNodes(BufferedWriter writer, HashMap<String, IdentifiedNode> nodes) throws IOException {
		// <nodes>
		writer.write("\n\t\t<nodes>");
		for(String type : NodeDefinition.uniqueTypes) {
			writer.write("\n\t\t\t<nodeclass type=\"" + "Agent" + "\" id=\"" + type + "\">");

			for(String key : nodes.keySet()) {

				IdentifiedNode c = nodes.get(key);
				if(c.type.equals(type)) {
					writer.write("\n\t\t\t\t<node id=\"" + c.id + "\">");
					for(String property : c.characteristics.keySet()) {
						String characteristic = c.characteristics.get(property);
						characteristic = characteristic.replaceAll("&", "+");
						writer.write("\n\t\t\t\t\t<property id=\"" + property + "\" value=\"" + characteristic + "\"/>");
					}
					writer.write("\n\t\t\t\t</node>");
				}
			}

			writer.write("\n\t\t\t</nodeclass>");
			//</nodes>
		}

		writer.write("\n\t\t</nodes>");
	}

	/**
	 * Write out each of the edges in DynetML format
	 * @param writer, BufferedWriter connected to the output file
	 * @param entities, the entities which have edges to write out to
	 * @throws Exception, in case writing to the fail fails
	 */
	static void writeDynetMLEdges(BufferedWriter writer, HashMap<String, IdentifiedNode> entities) throws Exception {

		writer.write("\n\t\t<networks>");

		for(String type : NodeDefinition.uniqueTypes) {
			writer.write("\n\t\t\t<network sourceType=\"" + "Agent" + 
					"\" source=\"" + "Respondent" + "\" targetType=\"" +
					"Agent" + "\" target=\"" + type + 
					"\" id=\"" + "Respondent x " + type + "\" isDirected=\"true\" allowSelfLoops=\"false\" isBinary=\"false\">");

			for(IdentifiedNode c : entities.values()) {
				
				for(String targetID : c.ties) {
					//System.out.println("Entities not listed:");
					if(entities.containsKey(targetID)) {
						IdentifiedNode target = entities.get(targetID);
						if(target.type.equals(type)) {
							writer.write("\n\t\t\t\t<link source=\"" + c.id + "\" target=\"" + 
									targetID + "\" value=\"" + "1" + "\"/>");
						}
					}
					else {
						System.out.println("Missing entity:\t" + targetID);
						
					}
					
				}	
			}

			writer.write("\n\t\t\t</network>");
		}
		writer.write("\n\t\t</networks>");

	}


	/**
	 * This does nothing currently, but could be used to instantate a default
	 * set of node definitions.
	 */
	static void setupNodeDefinitions() {
		//TODO consider adding load of a default set of definitions
	}
	
	/**
	 * Reads in a given data file and generates a collection of hashmaps, each hashmap represents
	 * a participant.
	 * 
	 * Note that we trim data as we get it, which removes white-spaces from in front and behind each
	 * element of the data.
	 * 
	 * @param dataFile The tab-delimited data-file to read
	 * @return a set of participant data 
	 * @throws Exception If someone goes wrong in reading the data file, we cancel execution
	 */
	static ArrayList<HashMap<String, String>> readDataFile(File dataFile) throws Exception {
		ArrayList<HashMap<String, String>> participantData = new ArrayList<HashMap<String, String>>();

		BufferedReader reader = new BufferedReader(new FileReader(dataFile));
		String headerLine = reader.readLine();
		String[] headerElements = headerLine.split(fileDelimiter);
		Collections.addAll(headers, headerElements);

		while(reader.ready()) {
			HashMap<String, String> participant = new HashMap<String, String>();
			String dataLine = reader.readLine();
			String[] dataElements = dataLine.split(fileDelimiter);
			for(int i = 0; i < dataElements.length; ++i) {
				if(headerElements.length > i) {
					if(!headerElements[i].equals("")) {
						String d = dataElements[i].trim();
						d = d.replaceAll("\"", "");
						participant.put(headerElements[i], d);
					}
				}
			}
			participantData.add(participant);
		}
		reader.close();

		return participantData;
	}
}
