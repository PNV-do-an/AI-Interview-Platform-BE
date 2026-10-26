const fs = require('fs');

const getAlScenesBody = JSON.stringify({
  projectId: "8",
  title: "Cat Sunset",
  sourceType: "SCRIPT_IDEA",
  status: "SCRIPT_REVIEW",
  sceneCount: 3,
  createdAt: "2026-10-22T07:31:24.609437",
  scenes: [
    {
      sceneId: "12",
      orderNo: 1,
      scriptStatus: "APPROVED",
      videoStatus: "NOT_STARTED",
      narration: "Một chú mèo cam vui vẻ chuẩn bị trượt dốc trên ngọn đồi cỏ xanh.",
      visualPrompt: "A cute orange cat preparing to slide down a lush green grassy hill, golden hour sunset, cinematic lighting, 4k",
      durationSec: 5
    },
    {
      sceneId: "13",
      orderNo: 2,
      scriptStatus: "APPROVED",
      videoStatus: "NOT_STARTED",
      narration: "Chú trượt xuống với tốc độ nhanh và biểu cảm vô cùng hào hứng.",
      visualPrompt: "An orange cat sliding down a steep hill happily, wind blowing its fur, sunset background, dynamic action shot",
      durationSec: 5
    },
    {
      sceneId: "14",
      orderNo: 3,
      scriptStatus: "PROPOSED",
      videoStatus: "NOT_STARTED",
      narration: "Kết thúc chuyến phiêu lưu dưới ánh hoàng hôn rực rỡ.",
      visualPrompt: "An orange cat stopping at the bottom of the hill, looking at the camera with a happy face, beautiful sunset, wide angle",
      durationSec: 5
    }
  ]
}, null, 2);

const generateScriptBody = JSON.stringify({
  projectId: "10",
  sceneCount: 3,
  scenes: []
}, null, 2);

const collection = {
  info: {
    name: "9router",
    schema: "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  item: [
    {
      name: "import-file-script",
      request: {
        method: "POST",
        header: [],
        body: {
          mode: "formdata",
          formdata: [
            {
              key: "file",
              type: "file",
              src: "",
              description: ""
            }
          ]
        },
        url: {
          raw: "http://localhost:8080/api/scripts/import/from-file",
          protocol: "http",
          host: ["localhost"],
          port: "8080",
          path: ["api", "scripts", "import", "from-file"],
          query: [{ key: "file", value: "" }]
        }
      },
      response: []
    },
    {
      name: "get-all-scenes",
      request: {
        method: "GET",
        header: [],
        url: {
          raw: "http://localhost:8080/api/scripts/8",
          protocol: "http",
          host: ["localhost"],
          port: "8080",
          path: ["api", "scripts", "8"]
        }
      },
      response: [
        {
          name: "200 OK",
          originalRequest: {
            method: "GET",
            header: [],
            url: {
              raw: "http://localhost:8080/api/scripts/8",
              protocol: "http",
              host: ["localhost"],
              port: "8080",
              path: ["api", "scripts", "8"]
            }
          },
          status: "OK",
          code: 200,
          _postman_previewlanguage: "json",
          header: [
            { key: "content-type", value: "application/json" }
          ],
          body: getAlScenesBody
        }
      ]
    },
    {
      name: "accept-scene",
      request: {
        method: "POST",
        header: [
          { key: "Content-Type", value: "application/json" }
        ],
        body: {
          mode: "raw",
          raw: JSON.stringify({ sceneId: 2, action: "reject", comment: "......." }, null, 2),
          options: { raw: { language: "json" } }
        },
        url: {
          raw: "http://localhost:8080/api/scripts/7/review",
          protocol: "http",
          host: ["localhost"],
          port: "8080",
          path: ["api", "scripts", "7", "review"]
        }
      },
      response: [
        {
          name: "200 OK",
          originalRequest: {
            method: "POST",
            header: [
              { key: "Content-Type", value: "application/json" }
            ],
            body: {
              mode: "raw",
              raw: JSON.stringify({ sceneId: 2, action: "reject", comment: "......." }, null, 2),
              options: { raw: { language: "json" } }
            },
            url: {
              raw: "http://localhost:8080/api/scripts/7/review",
              protocol: "http",
              host: ["localhost"],
              port: "8080",
              path: ["api", "scripts", "7", "review"]
            }
          },
          status: "OK",
          code: 200,
          _postman_previewlanguage: "text",
          header: [
            { key: "content-length", value: "0" }
          ],
          body: ""
        }
      ]
    },
    {
      name: "regenerate-scene",
      request: {
        method: "POST",
        header: [
          { key: "Content-Type", value: "application/json" }
        ],
        body: {
          mode: "raw",
          raw: JSON.stringify({ sceneId: 14, action: "reject", comment: "Tôi cần nó chi tiết hơn " }, null, 2),
          options: { raw: { language: "json" } }
        },
        url: {
          raw: "http://localhost:8080/api/scripts/8/regenerate",
          protocol: "http",
          host: ["localhost"],
          port: "8080",
          path: ["api", "scripts", "8", "regenerate"]
        }
      },
      response: [
        {
          name: "200 OK",
          originalRequest: {
            method: "POST",
            header: [
              { key: "Content-Type", value: "application/json" }
            ],
            body: {
              mode: "raw",
              raw: JSON.stringify({ sceneId: 14, action: "reject", comment: "Tôi cần nó chi tiết hơn" }, null, 2),
              options: { raw: { language: "json" } }
            },
            url: {
              raw: "http://localhost:8080/api/scripts/8/regenerate",
              protocol: "http",
              host: ["localhost"],
              port: "8080",
              path: ["api", "scripts", "8", "regenerate"]
            }
          },
          status: "OK",
          code: 200,
          _postman_previewlanguage: "text",
          header: [
            { key: "content-length", value: "0" }
          ],
          body: ""
        }
      ]
    },
    {
      name: "generate-script",
      request: {
        method: "POST",
        header: [
          { key: "Content-Type", value: "application/json" }
        ],
        body: {
          mode: "raw",
          raw: JSON.stringify({ content: "Tôi làm video ngắn về cách xây nhà, mô tả ngắn gọn ", title: "XÂY NHÀ TRONG 5S" }, null, 2),
          options: { raw: { language: "json" } }
        },
        url: {
          raw: "http://localhost:8080/api/scripts/import/from-text",
          protocol: "http",
          host: ["localhost"],
          port: "8080",
          path: ["api", "scripts", "import", "from-text"]
        }
      },
      response: [
        {
          name: "200 OK",
          originalRequest: {
            method: "POST",
            header: [
              { key: "Content-Type", value: "application/json" }
            ],
            body: {
              mode: "raw",
              raw: JSON.stringify({ content: "Tôi làm video ngắn về cách xây nhà, mô tả ngắn gọn", title: "XÂY NHÀ TRONG 5S" }, null, 2),
              options: { raw: { language: "json" } }
            },
            url: {
              raw: "http://localhost:8080/api/scripts/import/from-text",
              protocol: "http",
              host: ["localhost"],
              port: "8080",
              path: ["api", "scripts", "import", "from-text"]
            }
          },
          status: "OK",
          code: 200,
          _postman_previewlanguage: "json",
          header: [
            { key: "content-type", value: "application/json" }
          ],
          body: generateScriptBody
        }
      ]
    }
  ]
};

fs.writeFileSync('C:/Users/LENOVO/Downloads/9router_collection.json', JSON.stringify(collection, null, 2), 'utf8');
console.log('Done! File written successfully.');
console.log('Items count:', collection.item.length);
collection.item.forEach(function(i) {
  console.log(' -', i.name, '| responses:', i.response.length);
});
