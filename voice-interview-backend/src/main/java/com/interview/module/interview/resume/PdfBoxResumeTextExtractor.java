package com.interview.module.interview.resume;

import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.interview.common.exception.AppException;
import com.interview.module.media.service.StoredMediaFile;

@Service
public class PdfBoxResumeTextExtractor implements ResumeTextExtractor {

	private final int maxPages;
	private final int maxTextChars;

	public PdfBoxResumeTextExtractor(
			@Value("${app.interview.resume.max-pages:8}") int maxPages,
			@Value("${app.interview.resume.max-text-chars:12000}") int maxTextChars
	) {
		this.maxPages = Math.max(1, maxPages);
		this.maxTextChars = Math.max(1000, maxTextChars);
	}

	@Override
	public ResumeText extract(StoredMediaFile storedMediaFile) {
		try (PDDocument document = PDDocument.load(storedMediaFile.path().toFile())) {
			int totalPages = document.getNumberOfPages();
			int readablePages = Math.min(totalPages, maxPages);

			PDFTextStripper textStripper = new PDFTextStripper();
			textStripper.setSortByPosition(true);
			textStripper.setStartPage(1);
			textStripper.setEndPage(readablePages);

			String normalizedText = normalize(textStripper.getText(document));
			if (normalizedText.length() > maxTextChars) {
				normalizedText = normalizedText.substring(0, maxTextChars).trim();
			}

			return new ResumeText(normalizedText, readablePages, normalizedText.length());
		} catch (IOException ex) {
			throw new AppException(
					"RESUME_PARSE_FAILED",
					HttpStatus.BAD_REQUEST,
					"简历 PDF 解析失败，请上传有效的 PDF 文件",
					ex
			);
		}
	}

	private String normalize(String value) {
		if (!StringUtils.hasText(value)) {
			return "";
		}
		return value
				.replace("\r\n", "\n")
				.replace('\r', '\n')
				.replace('\u00A0', ' ')
				.replaceAll("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]", " ")
				.replaceAll("[ \\t]+", " ")
				.replaceAll("\\n{3,}", "\n\n")
				.trim();
	}
}
