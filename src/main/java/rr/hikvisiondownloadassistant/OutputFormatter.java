// Copyright (c) 2020 Ryan Richard

package rr.hikvisiondownloadassistant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import rr.hikvisiondownloadassistant.Model.SearchMatchItem;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static rr.hikvisiondownloadassistant.DateConverter.*;

@RequiredArgsConstructor
public class OutputFormatter {

    private enum MediaType {
        PHOTO,
        VIDEO
    }

    private final Options options;
    private final List<SearchMatchItem> videos;
    private final List<SearchMatchItem> photos;

    // TODO support outputting videos as a VLC playlist file for easy previewing?

    public void printResults() {
        List<OutputRow> rows = convertToOutputRows(MediaType.VIDEO, videos);
        rows.addAll(convertToOutputRows(MediaType.PHOTO, photos));

        rows.sort(Comparator.comparing(OutputRow::getStartTime));

        if (options.getOutputFormat().equals(Options.OutputFormat.table)) {
            printTableOutput(rows);
        } else {
            printJsonOutput(rows);
        }
    }

    private void printJsonOutput(List<OutputRow> rows) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rows));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private void printTableOutput(List<OutputRow> rows) {
        String tableColumnDelimiter = options.getTableDelimiter();

        if (!options.isQuiet()) {
            String headers = String.join(tableColumnDelimiter, List.of("Type", "EventType", "Start", "End", "Curl"));
            String underline = new String(new char[headers.length()]).replace("\0", "-");
            System.err.println(headers);
            System.err.println(underline);
        }

        rows.stream()
                .map(OutputRow::toTextTableRow)
                .forEach(row -> System.out.println(String.join(tableColumnDelimiter, row)));
    }

    private List<OutputRow> convertToOutputRows(MediaType mediaType, List<SearchMatchItem> items) {
        return items.stream().map(item -> new OutputRow(
                        item,
                        mediaType,
                        apiStringToDate(item.getTimeSpan().getStartTime()),
                        apiStringToDate(item.getTimeSpan().getEndTime())
                )
        ).collect(Collectors.toList());
    }

    @Data
    private class OutputRow {

        @Getter(value = AccessLevel.PRIVATE)
        private final SearchMatchItem item;

        private final MediaType mediaType;
        private final Date startTime;
        private final Date endTime;

        public List<String> toTextTableRow() {
            return List.of(
                    mediaType.toString(),
                    getEventType(),
                    dateToLocalString(startTime),
                    dateToLocalString(endTime),
                    getCurlCommand()
            );
        }

        public String getCurlCommand() {
            return mediaType == MediaType.PHOTO ? formatPhotoCurlCommand() : formatVideoCurlCommand();
        }

        public String getEventType() {
            return item.getMetadataMatches()
                    .getMetadataDescriptor()
                    .replace("recordType.meta.hikvision.com/", "")
                    .toUpperCase();
        }

        private String getPlaybackURI() {
            return item.getMediaSegmentDescriptor().getPlaybackURI();
        }

        private String formatVideoCurlCommand() {
            return String.join(" ", List.of(
                    "curl",
                    "-f",
                    "--anyauth --user " + options.getUsername() + ":" + getOutputPassword(),
                    "-X GET",
                    "-d '<downloadRequest><playbackURI>" + getPlaybackURI().replace("&", "&amp;") + "</playbackURI></downloadRequest>'",
                    "'http://" + options.getHost() + "/ISAPI/ContentMgmt/download'",
                    "--output " + dateToLocalFilenameString(startTime) + ".mp4"
            ));
        }

        private String formatPhotoCurlCommand() {
            return String.join(" ", List.of(
                    "curl",
                    "-f",
                    "--anyauth --user " + options.getUsername() + ":" + getOutputPassword(),
                    "'" + getPlaybackURI() + "'",
                    "--output " + dateToLocalFilenameString(startTime) + "." + item.getMediaSegmentDescriptor().getCodecType()
            ));
        }

        private String getOutputPassword() {
            return options.getOutputPassword() == null ? options.getPassword() : options.getOutputPassword();
        }

    }

}
