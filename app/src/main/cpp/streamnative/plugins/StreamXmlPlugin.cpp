#include "StreamXmlPlugin.h"

namespace streamnative {

StreamXmlPlugin::StreamXmlPlugin(bool includeTagsInOutput)
        : includeTagsInOutput_(includeTagsInOutput),
          state_(PluginState::IDLE),
          startState_(StartState::WAIT_LT),
          allowStartAfterEndTag_(false),
          allowStartAfterPunctuation_(false),
          haveEndPattern_(false) {
    reset();
}

PluginState StreamXmlPlugin::state() const {
    return state_;
}

bool StreamXmlPlugin::initPlugin() {
    reset();
    return true;
}

void StreamXmlPlugin::reset() {
    state_ = PluginState::IDLE;
    startState_ = StartState::WAIT_LT;
    tagName_.clear();
    endMatcher_.reset();
    endPattern_.clear();
    haveEndPattern_ = false;
}

bool StreamXmlPlugin::isAsciiLetter(char16_t c) {
    return (c >= u'A' && c <= u'Z') || (c >= u'a' && c <= u'z');
}

bool StreamXmlPlugin::isPunctuationTrigger(char16_t c) {
    switch (c) {
        case u'，':
        case u'。':
        case u'？':
        case u'！':
        case u'：':
        case u':':
        case u',':
        case u'.':
        case u'?':
        case u'!':
            return true;
        default:
            return false;
    }
}

bool StreamXmlPlugin::handleDefaultCharacter(char16_t c) {
    updatePunctuationAllowance(c);
    return true;
}

void StreamXmlPlugin::updatePunctuationAllowance(char16_t c) {
    if (isPunctuationTrigger(c)) {
        allowStartAfterPunctuation_ = true;
    } else if (c == u' ' || c == u'\t') {
        // keep
    } else {
        allowStartAfterPunctuation_ = false;
    }
}

bool StreamXmlPlugin::processStartMatcher(char16_t c) {
    switch (startState_) {
        case StartState::WAIT_LT: {
            if (c == u'<') {
                tagName_.clear();
                startState_ = StartState::WAIT_FIRST_LETTER;
                state_ = PluginState::TRYING;
            }
            return false;
        }
        case StartState::WAIT_FIRST_LETTER: {
            if (isAsciiLetter(c)) {
                tagName_.push_back(c);
                startState_ = StartState::IN_TAG_NAME;
                state_ = PluginState::TRYING;
                return false;
            }
            startState_ = StartState::WAIT_LT;
            state_ = PluginState::IDLE;
            return false;
        }
        case StartState::IN_TAG_NAME: {
            if (c == u' ') {
                startState_ = StartState::IN_ATTRS;
                state_ = PluginState::TRYING;
                return false;
            }
            if (c == u'>') {
                startState_ = StartState::WAIT_LT;
                state_ = PluginState::TRYING;
                return true;
            }
            tagName_.push_back(c);
            state_ = PluginState::TRYING;
            return false;
        }
        case StartState::IN_ATTRS: {
            if (c == u'>') {
                startState_ = StartState::WAIT_LT;
                state_ = PluginState::TRYING;
                return true;
            }
            state_ = PluginState::TRYING;
            return false;
        }
    }
    return false;
}

void StreamXmlPlugin::buildEndPattern() {
    endPattern_.clear();
    endPattern_.reserve(tagName_.size() + 3);
    endPattern_.push_back(u'<');
    endPattern_.push_back(u'/');
    endPattern_.append(tagName_);
    endPattern_.push_back(u'>');
    endMatcher_.setPattern(endPattern_);
    haveEndPattern_ = true;
}

bool StreamXmlPlugin::processChar(char16_t c, bool atStartOfLine) {
    if (state_ == PluginState::PROCESSING) {
        if (haveEndPattern_) {
            if (endMatcher_.process(c)) {
                allowStartAfterEndTag_ = true;
                allowStartAfterPunctuation_ = false;
                reset();
                return includeTagsInOutput_;
            }
        }
        return includeTagsInOutput_;
    }

    if (state_ == PluginState::IDLE && !atStartOfLine) {
        const bool allowStart = allowStartAfterEndTag_ || allowStartAfterPunctuation_;
        if (!allowStart) {
            return handleDefaultCharacter(c);
        }
        if (c == u' ' || c == u'\t') {
            return handleDefaultCharacter(c);
        }
    }

    const PluginState previousState = state_;
    const bool startMatched = processStartMatcher(c);

    if (startMatched) {
        state_ = PluginState::PROCESSING;
        allowStartAfterEndTag_ = false;
        allowStartAfterPunctuation_ = false;
        buildEndPattern();
        startState_ = StartState::WAIT_LT;
        return includeTagsInOutput_;
    }

    if (state_ == PluginState::TRYING) {
        allowStartAfterPunctuation_ = false;
        return includeTagsInOutput_;
    }

    if (previousState == PluginState::TRYING) {
        reset();
    }
    allowStartAfterEndTag_ = false;
    allowStartAfterPunctuation_ = false;
    return handleDefaultCharacter(c);
}

} // namespace streamnative
