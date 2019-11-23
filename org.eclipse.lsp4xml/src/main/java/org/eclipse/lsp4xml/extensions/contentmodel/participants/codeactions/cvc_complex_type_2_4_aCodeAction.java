/*******************************************************************************
* Copyright (c) 2019 Red Hat Inc. and others.
* All rights reserved. This program and the accompanying materials
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/l-v20.html
*
* Contributors:
*     Red Hat Inc. - initial API and implementation
*******************************************************************************/
package org.eclipse.lsp4xml.extensions.contentmodel.participants.codeactions;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4xml.commons.CodeActionFactory;
import org.eclipse.lsp4xml.dom.DOMDocument;
import org.eclipse.lsp4xml.dom.DOMElement;
import org.eclipse.lsp4xml.dom.DOMNode;
import org.eclipse.lsp4xml.extensions.contentmodel.model.CMDocument;
import org.eclipse.lsp4xml.extensions.contentmodel.model.CMElementDeclaration;
import org.eclipse.lsp4xml.extensions.contentmodel.model.ContentModelManager;
import org.eclipse.lsp4xml.services.extensions.ICodeActionParticipant;
import org.eclipse.lsp4xml.services.extensions.IComponentProvider;
import org.eclipse.lsp4xml.settings.XMLFormattingOptions;
import org.eclipse.lsp4xml.utils.LevenshteinDistance;
import org.eclipse.lsp4xml.utils.XMLPositionUtility;

/**
 * cvc_complex_type_2_4_a
 */
public class cvc_complex_type_2_4_aCodeAction implements ICodeActionParticipant {

	private static final float MAX_DISTANCE_DIFF_RATIO = 0.4f;

	@Override
	public void doCodeAction(Diagnostic diagnostic, Range range, DOMDocument document, List<CodeAction> codeActions,
			XMLFormattingOptions formattingSettings, IComponentProvider componentProvider) {
		try {
			int offset = document.offsetAt(diagnostic.getRange().getStart());
			DOMNode node = document.findNodeAt(offset);
			if (node != null && node.isElement()) {
				// Get element from the diagnostic
				DOMElement element = (DOMElement) node;
				String localName = element.getLocalName();

				Collection<CMElementDeclaration> possibleElements = getPossibleElements(element, componentProvider);
				if (possibleElements != null) {

					// When added to these collections, the names will be ordered alphabetically
					Collection<String> otherElementNames = new TreeSet<String>(Collator.getInstance());
					Collection<String> similarElementNames = new TreeSet<String>(Collator.getInstance());

					// Try to collect similar names coming from tag name
					for (CMElementDeclaration possibleElement : possibleElements) {
						String possibleElementName = possibleElement.getName();
						if (isSimilar(possibleElementName, localName)) {
							similarElementNames.add(possibleElementName);
						} else {
							otherElementNames.add(possibleElementName);
						}
					}

					// Create ranges for the replace.
					boolean selectLocalNameOnly = element.getPrefix() != null;
					List<Range> ranges = new ArrayList<>();
					Range startRange, endRange;
					if(selectLocalNameOnly) {
						startRange = XMLPositionUtility.selectStartTagLocalName(element);
						endRange = XMLPositionUtility.selectEndTagLocalName(element);
					}
					else {
						startRange = XMLPositionUtility.selectStartTagName(element);
						endRange = XMLPositionUtility.selectEndTagName(element);
					}
					ranges.add(startRange);
					
					if (endRange != null) {
						ranges.add(endRange);
					}

					if (!similarElementNames.isEmpty()) {
						// // Add code actions for each similar elements
						for (String elementName : similarElementNames) {
							CodeAction similarCodeAction = CodeActionFactory.replaceAt(
									"Did you mean '" + elementName + "'?", elementName, document.getTextDocument(),
									diagnostic, ranges);
							codeActions.add(similarCodeAction);
						}
					} else {
						// Add code actions for each possible elements
						for (String elementName : otherElementNames) {
							CodeAction otherCodeAction = CodeActionFactory.replaceAt(
									"Replace with '" + elementName + "'", elementName, document.getTextDocument(),
									diagnostic, ranges);
							codeActions.add(otherCodeAction);
						}
					}
				}
			}

		} catch (Exception e) {
			// Do nothing
		}
	}

	/**
	 * Returns the possible elements for the given DOM element.
	 * 
	 * @param element           the DOM element
	 * @param componentProvider the component provider
	 * @return the possible elements for the given DOM element.
	 * @throws Exception
	 */
	private static Collection<CMElementDeclaration> getPossibleElements(DOMElement element,
			IComponentProvider componentProvider) throws Exception {
		ContentModelManager contentModelManager = componentProvider.getComponent(ContentModelManager.class);

		String prefix = element.getPrefix();
		DOMElement parentElement = element.getParentElement();
		String parentPrefix = parentElement.getPrefix();
		// check if prefix is the same than the parent profix
		if (prefix != null && !prefix.equals(parentPrefix)) {
			// We are in the case 
			// <b:bean><camel:beani
			
			// returns the all element for the camel XML Schema.
			String namespaceURI = element.getNamespaceURI();
			CMDocument cmDocument = contentModelManager.findCMDocument(parentElement, namespaceURI);
			return cmDocument != null ? cmDocument.getElements() : null;
		}

		CMElementDeclaration cmElement = contentModelManager.findCMElement(parentElement);
		if (cmElement != null) {
			// Collect all possible elements from the parent element upon the offset start
			// of the element
			return cmElement.getPossibleElements(parentElement, element.getStart());
		}
		return null;
	}

	private static boolean isSimilar(String reference, String current) {
		int threshold = Math.round(MAX_DISTANCE_DIFF_RATIO * reference.length());
		LevenshteinDistance levenshteinDistance = new LevenshteinDistance(threshold);
		return levenshteinDistance.apply(reference, current) != -1;
	}
}