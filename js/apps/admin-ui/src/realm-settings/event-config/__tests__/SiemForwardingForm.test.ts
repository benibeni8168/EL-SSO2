import { describe, expect, it } from "vitest";
import { listToText, textToHeaders, textToList } from "../SiemForwardingForm";

describe("siem forwarding helpers", () => {
  it("converts lists to text and back", () => {
    expect(listToText(["LOGIN", "LOGOUT"])).toBe("LOGIN, LOGOUT");
    expect(textToList("LOGIN, LOGOUT")).toEqual(["LOGIN", "LOGOUT"]);
  });

  it("parses headers from text", () => {
    expect(textToHeaders("X-Test:1, X-Trace:abc")).toEqual({
      "X-Test": "1",
      "X-Trace": "abc",
    });
  });
});
