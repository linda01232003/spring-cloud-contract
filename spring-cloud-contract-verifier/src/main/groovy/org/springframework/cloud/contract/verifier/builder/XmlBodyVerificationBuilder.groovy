package org.springframework.cloud.contract.verifier.builder

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

import org.springframework.cloud.contract.spec.Contract
import org.springframework.cloud.contract.spec.internal.BodyMatcher
import org.springframework.cloud.contract.spec.internal.BodyMatchers
import org.springframework.cloud.contract.spec.internal.ExecutionProperty
import org.springframework.cloud.contract.spec.internal.MatchingType
import org.springframework.cloud.contract.verifier.util.xml.XmlToXPathsConverter

/**
 * @author Olga Maciaszek-Sharma
 * @since 2.1.0
 */
@PackageScope
@CompileStatic
class XmlBodyVerificationBuilder implements BodyMethodGeneration {

	private final Contract contract
	private final Optional<String> lineSuffix

	XmlBodyVerificationBuilder(Contract contract, Optional<String> lineSuffix) {
		this.contract = contract
		this.lineSuffix = lineSuffix
	}

	void addXmlResponseBodyCheck(BlockBuilder blockBuilder, Object responseBody,
								 BodyMatchers bodyMatchers, String responseString,
								 boolean shouldCommentOutBDDBlocks) {
		addXmlProcessingLines(blockBuilder, responseString)
		Object processedBody = XmlToXPathsConverter
				.removeMatchingXmlPaths(responseBody, bodyMatchers)
		List<BodyMatcher> matchers = new XmlToXPathsConverter()
				.mapToMatchers(processedBody)
		if (bodyMatchers?.hasMatchers()) {
			matchers.addAll(bodyMatchers.matchers())
		}
		addBodyMatchingBlock(matchers, blockBuilder, responseBody, shouldCommentOutBDDBlocks)
	}

	private void addXmlProcessingLines(BlockBuilder blockBuilder, String responseString) {
		['DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()',
		 "Document parsedXml = documentBuilder.parse(new InputSource(new StringReader($responseString)))"]
				.each {
			blockBuilder.addLine(it as String)
			addColonIfRequired(lineSuffix, blockBuilder)
		}
	}

	@Override
	void methodForNullCheck(BodyMatcher bodyMatcher, BlockBuilder bb) {
		String quotedAndEscapedPath = quotedAndEscaped(bodyMatcher.path())
		String method = "assertThat(nodeFromXPath(parsedXml, ${quotedAndEscapedPath})).isNull()"
		bb.addLine(method.replace('$', '\\$'))
		addColonIfRequired(lineSuffix, bb)
	}

	@Override
	void methodForEqualityCheck(BodyMatcher bodyMatcher, BlockBuilder bb, Object body) {
		Object retrievedValue =
				quotedAndEscaped(XmlToXPathsConverter.
						retrieveValue(bodyMatcher, body))
		String comparisonMethod = bodyMatcher
				.matchingType() == MatchingType.EQUALITY ? 'isEqualTo' : 'matches'
		String method = "assertThat(valueFromXPath(parsedXml, ${quotedAndEscaped(bodyMatcher.path())})).$comparisonMethod(${retrievedValue})"
		bb.addLine(method.replace('$', '\\$'))
		addColonIfRequired(lineSuffix, bb)
	}

	@Override
	void methodForCommandExecution(BodyMatcher bodyMatcher, BlockBuilder bb, Object body) {
		Object retrievedValue =
				quotedAndEscaped(XmlToXPathsConverter
						.retrieveValueFromBody(bodyMatcher.path(), body))
		ExecutionProperty property = bodyMatcher.value() as ExecutionProperty
		bb.addLine(property.insertValue(retrievedValue.replace('$', '\\$')))
		addColonIfRequired(lineSuffix, bb)
	}

	@Override
	void methodForTypeCheck(BodyMatcher bodyMatcher, BlockBuilder bb, Object copiedBody) {
		throw new UnsupportedOperationException("The `getNodeValue()` method in `org.w3c.dom.Node` always returns String.")
	}
}
