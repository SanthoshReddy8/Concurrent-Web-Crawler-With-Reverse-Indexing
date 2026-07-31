import http from "k6/http";
import { check } from "k6";

export const options = {
  stages: [
    { duration: "15s", target: 10 },
    { duration: "30s", target: 50 },
    { duration: "15s", target: 0 },
  ],
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<250"],
  },
};

const baseUrl = __ENV.BASE_URL || "http://localhost:7000";
const queries = ["distributed systems", "search engine", "java concurrency", "web crawler"];

export default function () {
  const query = encodeURIComponent(queries[__ITER % queries.length]);
  const response = http.get(`${baseUrl}/search?q=${query}&limit=10`);
  check(response, { "search returns 200": (result) => result.status === 200 });
}