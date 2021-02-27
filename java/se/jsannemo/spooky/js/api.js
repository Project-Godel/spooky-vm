goog.module("spooky.api");

const Compiler = goog.require('spooky.compiler.Compiler');

/**
 * @param {string} src
 * @return {!Array<string>}
 */
function compile(src) {
    const errs = [];
    const result = Compiler.compile(src);
    for (let i = 0; i < result.size(); i++) {
        errs.push(result.getAtIndex(i));
    }
    return errs;
}

goog.exportSymbol("spooky_compile", compile);

exports = {
    compile: compile,
};