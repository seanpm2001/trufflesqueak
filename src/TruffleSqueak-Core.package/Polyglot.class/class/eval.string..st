polyglot-api
eval: aLanguage string: aString
	^ self primitiveEval: aLanguage string: aString withUnixLineEndings inInnerContext: false