import DOMPurify from 'dompurify';
import { marked } from 'marked';

/** 题干 Markdown 安全渲染：marked 同步解析 + DOMPurify 消毒（XSS 防线）。 */
export function renderMarkdown(source: string): string {
  return DOMPurify.sanitize(marked.parse(source ?? '', { async: false }) as string);
}

/** Markdown 渲染容器：内容先经 renderMarkdown 消毒再注入，禁止直接传原始 HTML。 */
export default function MarkdownView({ source }: { source: string }) {
  return <div className="markdown-body" dangerouslySetInnerHTML={{ __html: renderMarkdown(source) }} />;
}
