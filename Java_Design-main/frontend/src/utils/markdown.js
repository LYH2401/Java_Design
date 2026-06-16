import { marked } from 'marked'
import hljs from 'highlight.js'

// Configure marked with highlight.js
marked.setOptions({
  breaks: true,
  gfm: true,
  highlight(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(code, { language: lang }).value
      } catch (_) {}
    }
    try {
      return hljs.highlightAuto(code).value
    } catch (_) {}
    return code
  }
})

export const renderMarkdown = (text) => {
  if (!text) return ''
  return marked.parse(text)
}
