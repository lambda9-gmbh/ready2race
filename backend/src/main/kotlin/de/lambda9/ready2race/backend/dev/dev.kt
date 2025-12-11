package de.lambda9.ready2race.backend.dev

import de.lambda9.ready2race.backend.app.email.control.EmailDebugRepo
import de.lambda9.ready2race.backend.calls.requests.optionalQueryParam
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.config.Config
import de.lambda9.ready2race.backend.parsing.Parser.Companion.int
import de.lambda9.tailwind.core.extensions.kio.orDie
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.format.DateTimeFormatter

fun Routing.devRoutes(config: Config) {
    // Only enable dev routes when not in PROD mode
    if (config.mode == Config.Mode.PROD) {
        return
    }

    get("/api/dev/emails") {
        call.respondComprehension {
            val search = !call.optionalQueryParam("search")
            val page = !call.optionalQueryParam("page", int) ?: 1
            val pageSize = !call.optionalQueryParam("pageSize", int) ?: 20

            val result = !EmailDebugRepo.listEmailsWithAttachments(search, page, pageSize).orDie()

            val html = generateEmailDebugHtml(result, search, page, pageSize)

            call.respondText(html, ContentType.Text.Html)

            noData
        }
    }
}

private fun generateEmailDebugHtml(
    result: EmailDebugRepo.EmailDebugResult,
    search: String?,
    currentPage: Int,
    pageSize: Int
): String {
    val totalPages = (result.totalCount + pageSize - 1) / pageSize
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    val shortDateFormatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm")
    val firstEmailId = result.emails.firstOrNull()?.id

    return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Email Debug Console</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }

        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
            background: #1a1a1a;
            color: #e0e0e0;
            line-height: 1.6;
            height: 100vh;
            overflow: hidden;
        }

        .app-container {
            display: flex;
            flex-direction: column;
            height: 100vh;
        }

        header {
            padding: 20px;
            background: #0f0f0f;
            border-bottom: 1px solid #2a2a2a;
        }

        h1 {
            font-size: 24px;
            margin-bottom: 8px;
            color: #ffffff;
        }

        .subtitle {
            color: #888;
            font-size: 13px;
        }

        .controls {
            padding: 15px 20px;
            background: #151515;
            border-bottom: 1px solid #2a2a2a;
            display: flex;
            gap: 15px;
            align-items: center;
            flex-wrap: wrap;
        }

        .search-box {
            flex: 1;
            min-width: 250px;
            max-width: 400px;
        }

        .search-box input {
            width: 100%;
            padding: 8px 12px;
            background: #2a2a2a;
            border: 1px solid #444;
            border-radius: 6px;
            color: #e0e0e0;
            font-size: 13px;
        }

        .search-box input:focus {
            outline: none;
            border-color: #4a90e2;
        }

        .stats {
            color: #888;
            font-size: 13px;
        }

        .mailbox-container {
            display: flex;
            flex: 1;
            overflow: hidden;
        }

        .email-list {
            width: 380px;
            background: #1a1a1a;
            border-right: 1px solid #2a2a2a;
            overflow-y: auto;
            display: flex;
            flex-direction: column;
        }

        .email-list-item {
            padding: 16px;
            border-bottom: 1px solid #2a2a2a;
            cursor: pointer;
            transition: background 0.15s;
        }

        .email-list-item:hover {
            background: #232323;
        }

        .email-list-item.selected {
            background: #2a2a2a;
            border-left: 3px solid #4a90e2;
        }

        .email-list-item.unread {
            background: #1f1f1f;
        }

        .email-header {
            display: flex;
            justify-content: space-between;
            align-items: flex-start;
            margin-bottom: 8px;
        }

        .email-from {
            font-weight: 600;
            color: #ffffff;
            font-size: 14px;
            flex: 1;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }

        .email-date {
            color: #888;
            font-size: 12px;
            margin-left: 10px;
            flex-shrink: 0;
        }

        .email-subject {
            font-size: 13px;
            color: #e0e0e0;
            margin-bottom: 4px;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }

        .email-preview {
            font-size: 12px;
            color: #888;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }

        .email-meta {
            display: flex;
            gap: 8px;
            margin-top: 6px;
            align-items: center;
        }

        .status-badge {
            display: inline-block;
            padding: 2px 8px;
            border-radius: 3px;
            font-size: 11px;
            font-weight: 600;
        }

        .status-badge.sent {
            background: #1e5631;
            color: #4ade80;
        }

        .status-badge.pending {
            background: #4a3a1e;
            color: #fbbf24;
        }

        .status-badge.error {
            background: #5e1e1e;
            color: #f87171;
        }

        .email-detail {
            flex: 1;
            background: #1a1a1a;
            overflow-y: auto;
            display: flex;
            flex-direction: column;
        }

        .email-detail.empty {
            display: flex;
            align-items: center;
            justify-content: center;
            color: #666;
            font-size: 14px;
        }

        .detail-header {
            padding: 24px;
            background: #1f1f1f;
            border-bottom: 1px solid #2a2a2a;
        }

        .detail-subject {
            font-size: 22px;
            font-weight: 600;
            color: #ffffff;
            margin-bottom: 16px;
        }

        .detail-meta {
            display: flex;
            flex-direction: column;
            gap: 8px;
            font-size: 13px;
        }

        .detail-meta-row {
            display: flex;
            gap: 12px;
        }

        .detail-label {
            color: #888;
            min-width: 60px;
        }

        .detail-value {
            color: #e0e0e0;
            flex: 1;
        }

        .detail-body {
            padding: 24px;
            flex: 1;
        }

        .content-body {
            background: #0f0f0f;
            padding: 20px;
            border-radius: 8px;
            border: 1px solid #2a2a2a;
            line-height: 1.6;
        }

        .content-body.html-content {
            background: #ffffff;
            color: #000000;
        }

        .content-body.text-content {
            white-space: pre-wrap;
            word-wrap: break-word;
            font-size: 14px;
            color: #e0e0e0;
        }

        .attachments-section {
            margin-top: 24px;
            padding-top: 24px;
            border-top: 1px solid #2a2a2a;
        }

        .attachments-title {
            font-size: 14px;
            font-weight: 600;
            color: #ffffff;
            margin-bottom: 12px;
        }

        .attachments-list {
            display: flex;
            flex-wrap: wrap;
            gap: 12px;
        }

        .attachment {
            background: #2a2a2a;
            padding: 12px 16px;
            border-radius: 6px;
            border: 1px solid #3a3a3a;
            display: flex;
            align-items: center;
            gap: 10px;
        }

        .attachment-icon {
            font-size: 20px;
        }

        .attachment-info {
            flex: 1;
        }

        .attachment-name {
            color: #4a90e2;
            font-weight: 600;
            font-size: 13px;
        }

        .attachment-meta {
            color: #888;
            font-size: 11px;
            margin-top: 2px;
        }

        .error-section {
            margin-top: 24px;
            padding: 16px;
            background: #3a1e1e;
            border: 1px solid #5e1e1e;
            border-radius: 8px;
        }

        .error-title {
            font-size: 14px;
            font-weight: 600;
            color: #f87171;
            margin-bottom: 8px;
        }

        .error-message {
            color: #f87171;
            font-family: monospace;
            font-size: 12px;
            white-space: pre-wrap;
        }

        .pagination {
            padding: 12px 16px;
            background: #151515;
            border-top: 1px solid #2a2a2a;
            display: flex;
            justify-content: center;
            gap: 10px;
            align-items: center;
        }

        .pagination button, .pagination span {
            padding: 6px 12px;
            background: #2a2a2a;
            border: 1px solid #444;
            border-radius: 6px;
            color: #e0e0e0;
            cursor: pointer;
            font-size: 13px;
            transition: all 0.15s;
        }

        .pagination button:hover:not(:disabled) {
            background: #333;
            border-color: #4a90e2;
        }

        .pagination button:disabled {
            opacity: 0.4;
            cursor: not-allowed;
        }

        .pagination .current-page {
            background: #4a90e2;
            border-color: #4a90e2;
            cursor: default;
        }

        @media (max-width: 1024px) {
            .email-list {
                width: 300px;
            }
        }

        @media (max-width: 768px) {
            .mailbox-container {
                flex-direction: column;
            }

            .email-list {
                width: 100%;
                max-height: 40vh;
                border-right: none;
                border-bottom: 1px solid #2a2a2a;
            }
        }
    </style>
</head>
<body>
    <div class="app-container">
        <header>
            <h1>📧 Email Debug Console</h1>
            <p class="subtitle">Monitor and debug email queue in development mode</p>
        </header>

        <div class="controls">
            <div class="search-box">
                <input
                    type="text"
                    id="searchInput"
                    placeholder="Search by recipient email..."
                    value="${search?.let { escapeHtml(it) } ?: ""}"
                >
            </div>
            <div class="stats">
                Total: ${result.totalCount} emails | Page $currentPage of $totalPages
            </div>
        </div>

        <div class="mailbox-container">
            <div class="email-list">
                ${
        result.emails.joinToString("") { email ->
            val status = when {
                email.error != null -> "error"
                email.sentAt != null -> "sent"
                else -> "pending"
            }
            val statusText = when {
                email.error != null -> "Error"
                email.sentAt != null -> "Sent"
                else -> "Pending"
            }

            val preview = email.body.take(80).replace("\n", " ")

            """
                    <div class="email-list-item" onclick="selectEmail('${
                email.id.toString()
            }')" id="email-item-${email.id.toString()}">
                        <div class="email-header">
                            <div class="email-date">${email.createdAt.format(shortDateFormatter)}</div>
                        </div>
                        <div class="email-subject">${escapeHtml(email.subject)}</div>
                        <div class="email-preview">${escapeHtml(preview)}${if (email.body.length > 80) "..." else ""}</div>
                        <div class="email-meta">
                            <span class="status-badge $status">$statusText</span>
                            ${if (email.attachments.isNotEmpty()) """<span style="font-size: 11px; color: #888;">📎 ${email.attachments.size}</span>""" else ""}
                        </div>
                    </div>
                    """
        }
    }
            </div>

            <div class="email-detail ${if (result.emails.isEmpty()) "empty" else ""}" id="emailDetail">
                ${
        if (result.emails.isEmpty()) {
            "<div>No emails to display</div>"
        } else {
            result.emails.firstOrNull()?.let { email ->
                val status = when {
                    email.error != null -> "error"
                    email.sentAt != null -> "sent"
                    else -> "pending"
                }
                val statusText = when {
                    email.error != null -> "Error"
                    email.sentAt != null -> "Sent"
                    else -> "Pending"
                }

                """
                        <div class="detail-header">
                            <div class="detail-subject">${escapeHtml(email.subject)}</div>
                            <div class="detail-meta">
                                <div class="detail-meta-row">
                                    <span class="detail-label">To:</span>
                                    <span class="detail-value">${escapeHtml(email.recipient)}</span>
                                </div>
                                <div class="detail-meta-row">
                                    <span class="detail-label">Status:</span>
                                    <span class="detail-value"><span class="status-badge $status">$statusText</span></span>
                                </div>
                                <div class="detail-meta-row">
                                    <span class="detail-label">Date:</span>
                                    <span class="detail-value">${email.createdAt.format(dateFormatter)}</span>
                                </div>
                                ${
                    if (email.sentAt != null) """
                                <div class="detail-meta-row">
                                    <span class="detail-label">Sent:</span>
                                    <span class="detail-value">${email.sentAt.format(dateFormatter)}</span>
                                </div>
                                """ else ""
                }
                            </div>
                        </div>
                        <div class="detail-body">
                            ${
                    if (email.error != null) """
                                <div class="error-section">
                                    <div class="error-title">⚠️ Error</div>
                                    <div class="error-message">${escapeHtml(email.error)}</div>
                                </div>
                            """ else ""
                }

                            <div class="content-body ${if (email.bodyIsHtml) "html-content" else "text-content"}">
                                ${if (email.bodyIsHtml) email.body else escapeHtml(email.body)}
                            </div>

                            ${
                    if (email.attachments.isNotEmpty()) """
                                <div class="attachments-section">
                                    <div class="attachments-title">📎 Attachments (${email.attachments.size})</div>
                                    <div class="attachments-list">
                                        ${
                        email.attachments.joinToString("") { attachment ->
                            """
                                            <div class="attachment">
                                                <div class="attachment-icon">📄</div>
                                                <div class="attachment-info">
                                                    <div class="attachment-name">${escapeHtml(attachment.filename ?: "unknown")}</div>
                                                </div>
                                            </div>
                                            """
                        }
                    }
                                    </div>
                                </div>
                            """ else ""
                }
                        </div>
                        """
            } ?: ""
        }
    }
            </div>
        </div>

        <div class="pagination">
            <button onclick="goToPage(${currentPage - 1})" ${if (currentPage <= 1) "disabled" else ""}>
                ← Previous
            </button>
            <span class="current-page">Page $currentPage of $totalPages</span>
            <button onclick="goToPage(${currentPage + 1})" ${if (currentPage >= totalPages) "disabled" else ""}>
                Next →
            </button>
        </div>
    </div>

    <script>
        const emailsData = ${
        if (result.emails.isEmpty()) {
            "[]"
        } else {
            result.emails.joinToString(",", "[", "]") { email ->
                val status = when {
                    email.error != null -> "error"
                    email.sentAt != null -> "sent"
                    else -> "pending"
                }
                val statusText = when {
                    email.error != null -> "Error"
                    email.sentAt != null -> "Sent"
                    else -> "Pending"
                }

                val attachmentsJson = if (email.attachments.isEmpty()) {
                    ""
                } else {
                    email.attachments.joinToString(",") { attachment ->
                        """{"filename":${(attachment.filename ?: "unknown").let { escapeHtml(it).toJsonString() }}}"""
                    }
                }

                buildString {
                    append("{")
                    append("\"id\":${email.id.toString().toJsonString()},")
                    append("\"subject\":${escapeHtml(email.subject).toJsonString()},")
                    append("\"body\":${if (email.bodyIsHtml) email.body.toJsonString() else escapeHtml(email.body).toJsonString()},")
                    append("\"bodyIsHtml\":${email.bodyIsHtml},")
                    append("\"recipient\":${escapeHtml(email.recipient).toJsonString()},")
                    append("\"sentAt\":${email.sentAt?.format(dateFormatter)?.toJsonString() ?: "null"},")
                    append("\"createdAt\":${email.createdAt.format(dateFormatter).toJsonString()},")
                    append("\"status\":${status.toJsonString()},")
                    append("\"statusText\":${statusText.toJsonString()},")
                    append("\"error\":${email.error?.let { escapeHtml(it).toJsonString() } ?: "null"},")
                    append("\"attachments\":[$attachmentsJson]")
                    append("}")
                }
            }
        }
    };

        let currentEmailId = ${firstEmailId?.toString()?.toJsonString() ?: "null"};

        function selectEmail(id) {
            currentEmailId = id;

            // Update selected state in list
            document.querySelectorAll('.email-list-item').forEach(item => {
                item.classList.remove('selected');
            });
            document.getElementById('email-item-' + id).classList.add('selected');

            // Find email data
            const email = emailsData.find(e => e.id === id);
            if (!email) return;

            // Update detail view
            const detailContainer = document.getElementById('emailDetail');
            detailContainer.className = 'email-detail';

            let attachmentsHtml = '';
            if (email.attachments.length > 0) {
                attachmentsHtml = `
                    <div class="attachments-section">
                        <div class="attachments-title">📎 Attachments (${'$'}{email.attachments.length})</div>
                        <div class="attachments-list">
                            ${'$'}{email.attachments.map(att => `
                                <div class="attachment">
                                    <div class="attachment-icon">📄</div>
                                    <div class="attachment-info">
                                        <div class="attachment-name">${'$'}{att.filename}</div>
                                    </div>
                                </div>
                            `).join('')}
                        </div>
                    </div>
                `;
            }

            let errorHtml = '';
            if (email.error) {
                errorHtml = `
                    <div class="error-section">
                        <div class="error-title">⚠️ Error</div>
                        <div class="error-message">${'$'}{email.error}</div>
                    </div>
                `;
            }

            detailContainer.innerHTML = `
                <div class="detail-header">
                    <div class="detail-subject">${'$'}{email.subject}</div>
                    <div class="detail-meta">
                        <div class="detail-meta-row">
                            <span class="detail-label">To:</span>
                            <span class="detail-value">${'$'}{email.recipient}</span>
                        </div>
                        <div class="detail-meta-row">
                            <span class="detail-label">Status:</span>
                            <span class="detail-value"><span class="status-badge ${'$'}{email.status}">${'$'}{email.statusText}</span></span>
                        </div>
                        <div class="detail-meta-row">
                            <span class="detail-label">Date:</span>
                            <span class="detail-value">${'$'}{email.createdAt}</span>
                        </div>
                        ${'$'}{email.sentAt ? `
                        <div class="detail-meta-row">
                            <span class="detail-label">Sent:</span>
                            <span class="detail-value">${'$'}{email.sentAt}</span>
                        </div>
                        ` : ''}
                    </div>
                </div>
                <div class="detail-body">
                    ${'$'}{errorHtml}
                    <div class="content-body ${'$'}{email.bodyIsHtml ? 'html-content' : 'text-content'}">
                        ${'$'}{email.body}
                    </div>
                    ${'$'}{attachmentsHtml}
                </div>
            `;
        }

        function goToPage(page) {
            const url = new URL(window.location);
            url.searchParams.set('page', page);
            window.location.href = url.toString();
        }

        const searchInput = document.getElementById('searchInput');
        let searchTimeout;

        searchInput.addEventListener('input', function(e) {
            clearTimeout(searchTimeout);
            searchTimeout = setTimeout(function() {
                const url = new URL(window.location);
                if (e.target.value) {
                    url.searchParams.set('search', e.target.value);
                } else {
                    url.searchParams.delete('search');
                }
                url.searchParams.set('page', '1');
                window.location.href = url.toString();
            }, 500);
        });

        searchInput.addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                clearTimeout(searchTimeout);
                const url = new URL(window.location);
                if (e.target.value) {
                    url.searchParams.set('search', e.target.value);
                } else {
                    url.searchParams.delete('search');
                }
                url.searchParams.set('page', '1');
                window.location.href = url.toString();
            }
        });

        if (searchInput.value) {
            searchInput.focus();
            searchInput.setSelectionRange(searchInput.value.length, searchInput.value.length);
        }

        // Select first email on load
        if (currentEmailId) {
            document.getElementById('email-item-' + currentEmailId)?.classList.add('selected');
        }
    </script>
</body>
</html>
    """.trimIndent()
}

private fun String.toJsonString(): String {
    return buildString {
        append('"')
        this@toJsonString.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 32) {
                    append("\\u%04x".format(char.code))
                } else {
                    append(char)
                }
            }
        }
        append('"')
    }
}

private fun escapeHtml(text: String): String {
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}