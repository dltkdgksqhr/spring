package kr.co.ezfram.aims.controller;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.transform.Result;

import org.apache.commons.io.IOUtils;
import org.elasticsearch.client.RestClient;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thymeleaf.util.MapUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import kr.co.ezfram.aims.elastic.ElasticResultMap;
import kr.co.ezfram.aims.elastic.service.ElasticService;
import kr.co.ezfram.aims.elastic.service.impl.ElasticServiceImpl;
import kr.co.ezfram.aims.enumeration.ApiUrl;
import kr.co.ezfram.aims.service.AuthService;
import kr.co.ezfram.aims.service.QnaService;
import kr.co.ezfram.aims.util.HttpUtil;
import kr.co.ezfram.aims.util.SHA256;
import kr.co.ezfram.aims.vo.AnswerVo;
import kr.co.ezfram.aims.vo.QuestionVo;
import kr.co.ezfram.aims.vo.TokenVo;


@Controller
@RequestMapping("/qna")
public class QnaController {

	@Autowired
	private SHA256 sha256;
	
	@Autowired
	private AuthService authService;
	
	@Autowired
	private QnaService qnaService;
	
	@Value("${aiad.api.id}")
	private String apiId;
	
	@Value("${minio.url}")
	private String minioUrl;
	
	@Value("${minio.id}")
	private String minioId;

	@Value("${minio.pw}")
	private String minioPw;
	
	Logger logger = LoggerFactory.getLogger(QnaController.class);
	
	
	// ?????? ????????? ?????????
	@RequestMapping("/getResult")
	public ResponseEntity<?> getResult(HttpServletRequest request, Model model, @RequestBody Map<String, Object> param) throws Exception {
		
		HttpSession httpSession = request.getSession(true);
		//String userId = (String) httpSession.getAttribute("USER_ID");
		String userId = apiId;
		
		TokenVo tokenVo = authService.selectRefreshToken(userId);
		String token = tokenVo.getRefreshToken();
		
		// ??????
		String question = (String) param.get("question");
		//SHA256?????? ???????????? ????????????
        String questionHash = sha256.encrypt(question);
        String apiParam = "";
        
        String ansType = (String) param.get("ansType");	// ?????? ?????? : ??????/??????/??????
        String searchType = (String) param.get("searchType");	// ?????? ?????? : ????????????/????????????
        
        ObjectMapper mapper = new ObjectMapper();
        
        long beforeTime1 = System.currentTimeMillis();
        
        String index = "";
		String qry = "";
		
		ElasticResultMap pdfResult = null;
		ElasticResultMap articleResult = null;
		ElasticResultMap guideResult = null;
		
		List<Map<String, Object>> pdfParagraphSources = null;
		List<Map<String, Object>> articleParagraphSources = null;
		List<Map<String, Object>> guideParagraphSources = null;
		
		List<Map<String, Object>> paragraphResultSources = new ArrayList<Map<String,Object>>();
		List<Map<String, Object>> splitParagraphResultSources = new ArrayList<Map<String,Object>>();
		
		int pdfParagraphArrSize = 50;
		int articleParagraphArrSize = 600;
		int guideParagraphArrSize = 600;
		
		if("all".equals(ansType)) {
			pdfResult = qnaService.getPdfParagraphResult(question);
			articleResult = qnaService.getArticleParagraphResult(question);
			guideResult = qnaService.getGuideParagraphResult(question);
			
			// ?????? ?????? (?????? score ?????? + ?????? ???????????? ??????)
			pdfParagraphSources = qnaService.getRankedParagraphList(pdfResult.getSources());
			articleParagraphSources = qnaService.getRankedParagraphList(articleResult.getSources());
			guideParagraphSources = qnaService.getRankedParagraphList(guideResult.getSources());
			
			if(pdfParagraphSources.size() < 50) {
				pdfParagraphArrSize = pdfParagraphSources.size();
			}
			
			if(articleParagraphSources.size() < 600) {
				articleParagraphArrSize = articleParagraphSources.size();
			}
			
			if(guideParagraphSources.size() < 600) {
				guideParagraphArrSize = guideParagraphSources.size();
			}
			
			// ?????? ?????? ?????????
			pdfParagraphSources = qnaService.getParagraphResultByDocId(pdfParagraphSources.subList(0, pdfParagraphArrSize), question);
			articleParagraphSources = qnaService.getParagraphResultByDocId(articleParagraphSources.subList(0, articleParagraphArrSize), question);
			guideParagraphSources = qnaService.getParagraphResultByDocId(guideParagraphSources.subList(0, guideParagraphArrSize), question);
			
			paragraphResultSources.addAll(pdfParagraphSources);
			paragraphResultSources.addAll(articleParagraphSources);
			paragraphResultSources.addAll(guideParagraphSources);
			
		} else if("pdf".equals(ansType)) {
			pdfResult = qnaService.getPdfParagraphResult(question);
			
			pdfParagraphSources = qnaService.getRankedParagraphList(pdfResult.getSources());
			
			if(pdfParagraphSources.size() < 50) {
				pdfParagraphArrSize = pdfParagraphSources.size();
			}
			
			pdfParagraphSources = qnaService.getParagraphResultByDocId(pdfParagraphSources.subList(0, pdfParagraphArrSize), question);
			
			paragraphResultSources.addAll(pdfParagraphSources);
			
		} else if("article".equals(ansType)) {
			articleResult = qnaService.getArticleParagraphResult(question);
			
			articleParagraphSources = qnaService.getRankedParagraphList(articleResult.getSources());
			
			if(articleParagraphSources.size() < 600) {
				articleParagraphArrSize = articleParagraphSources.size();
			}
			
			articleParagraphSources = qnaService.getParagraphResultByDocId(articleParagraphSources.subList(0, articleParagraphArrSize), question);
			
			paragraphResultSources.addAll(articleParagraphSources);
			
		}  else if("guide".equals(ansType)) {
			guideResult = qnaService.getGuideParagraphResult(question);
			
			guideParagraphSources = qnaService.getRankedParagraphList(guideResult.getSources());
			
			if(guideParagraphSources.size() < 600) {
				guideParagraphArrSize = guideParagraphSources.size();
			}
			
			guideParagraphSources = qnaService.getParagraphResultByDocId(guideParagraphSources.subList(0, guideParagraphArrSize), question);
			
			paragraphResultSources.addAll(guideParagraphSources);
		}
		
		// ?????????????????? ???????????????(score) ?????????????????? ??????
		Collections.sort(paragraphResultSources, new Comparator<Map<String, Object>>() {
			@Override
			public int compare(Map<String, Object> o1, Map<String, Object> o2) {
				String strProb1 = String.valueOf(o1.get("score")) == ""? "0" : String.valueOf(o1.get("score"));
				String strProb2 = String.valueOf(o2.get("score")) == ""? "0" : String.valueOf(o2.get("score"));
				
				Double prob1 = Double.parseDouble(strProb1);
				Double prob2 = Double.parseDouble(strProb2);
				
				prob1 = (prob1 == null) ? 0 : prob1;
				prob2 = (prob2 == null) ? 0 : prob2;
				
				return prob2.compareTo(prob1);
			}
		});
		
		//double afterTime1 = System.currentTimeMillis(); // ?????? ?????? ?????? ?????? ????????????
		//double secDiffTime1 = (afterTime1 - beforeTime1)/1000; //??? ????????? ??? ??????
		//System.out.println("elastic size 200 ????????????(m) : "+secDiffTime1);
		
		int searchVolum = 0;
		// ?????? : 20 , ?????? : 240
        if("simple".equals(searchType)) {
        	searchVolum = 20;
        } else if("exact".equals(searchType)) {
        	searchVolum = 240;
        }
		
		List<HashMap<String, Object>> mrcResultMapList = new ArrayList<HashMap<String, Object>>();
		// ??????/?????? ????????? ?????? ?????? ????????? ???????????? ???????????? api ??????
		mrcResultMapList = qnaService.getMrcResultMapList(token, question, paragraphResultSources.subList(0, searchVolum), searchType);
		
		// mrc ?????? ????????? docId??? ???????????? aiad_paragraph source ?????? ??????
		List<Map<String, Object>> mrcParagraphSources = new ArrayList<Map<String,Object>>();
		
		for(int i=0; i<mrcResultMapList.size(); i++) {
			String docId = (String) mrcResultMapList.get(i).get("docId");
			String mrcScoreStr = String.valueOf(mrcResultMapList.get(i).get("probability")) == ""? "0" : String.valueOf(mrcResultMapList.get(i).get("probability"));
			Double mrcScore = Double.parseDouble(mrcScoreStr);
			
			// ??????????????? value?????? mrc score??? ???????????? mrc docId??? ???????????? ?????????????????? mrc score??? ?????? 
			Map result = paragraphResultSources.stream().filter(x -> x.get("doc_id_STR").equals(docId)).findAny().get();
			result.put("mrcScore", mrcScore);
			
			mrcParagraphSources.add(result);
		}
		
		// ElasticSearch (index - aiad_doc)?????? ????????? return
		List<Map<String, Object>> aiadDocResultSources = new ArrayList<Map<String,Object>>();
		aiadDocResultSources = qnaService.getAiadDocList(mrcResultMapList);
		
		Map<String, Object> res = new HashMap<>();
		res.put("mrcResultMapList", mrcResultMapList);	// ???????????? ?????? list
		res.put("mrcParagraphSources", mrcParagraphSources);	// ElasticSearch (index - aiad_paragraph)?????? ?????? source
		res.put("aiadDocResultSources", aiadDocResultSources);	// ElasticSearch (index - aiad_doc)?????? ?????? source
		res.put("paramMap", param);	// ?????? parameter
		
		res.put("pdfParagraphSources", pdfParagraphSources);
		res.put("articleParagraphSources", articleParagraphSources);
		res.put("guideParagraphSources", guideParagraphSources);
		
		return new ResponseEntity<>(res, HttpStatus.OK);
	}
	
	// ???????????? ?????? ?????????
	@RequestMapping("/getMoreResult")
	public ResponseEntity<?> getMoreResult(HttpServletRequest request, Model model, @RequestBody Map<String, Object> param) throws Exception {
		
		HttpSession httpSession = request.getSession(true);
		//String userId = (String) httpSession.getAttribute("USER_ID");
		String userId = apiId;
		
		TokenVo tokenVo = authService.selectRefreshToken(userId);
		String token = tokenVo.getRefreshToken();
		
		// ??????
		String question = (String) param.get("question");
		
        List<Map<String, Object>> paragraphResultSources = (List<Map<String, Object>>) param.get("moreParagraphList");
        List<HashMap<String, Object>> moreMrcResultMapList = new ArrayList<HashMap<String, Object>>();
        moreMrcResultMapList = qnaService.getMrcResultMapList(token, question, paragraphResultSources, null);
        
        // mrc ?????? ????????? docId??? ???????????? aiad_paragraph source ?????? ??????
 		List<Map<String, Object>> moreMrcParagraphSources = new ArrayList<Map<String,Object>>();
 		
 		for(int i=0; i<moreMrcResultMapList.size(); i++) {
 			String docId = (String) moreMrcResultMapList.get(i).get("docId");
 			String mrcScoreStr = String.valueOf(moreMrcResultMapList.get(i).get("probability")) == ""? "0" : String.valueOf(moreMrcResultMapList.get(i).get("probability"));
			Double mrcScore = Double.parseDouble(mrcScoreStr);
			
 			Map result = paragraphResultSources.stream().filter(x -> x.get("doc_id_STR").equals(docId)).findAny().get();
 			result.put("mrcScore", mrcScore);
 			
 			moreMrcParagraphSources.add(result);
 		}
 		
 		// ElasticSearch (index - aiad_doc)?????? ????????? return
 		List<Map<String, Object>> moreAiadDocResultSources = new ArrayList<Map<String,Object>>();
 		moreAiadDocResultSources = qnaService.getAiadDocList(moreMrcResultMapList);
		
		Map<String, Object> res = new HashMap<>();
		res.put("moreMrcResultMapList", moreMrcResultMapList);	// ???????????? ?????? list
		res.put("moreMrcParagraphSources", moreMrcParagraphSources);	// ElasticSearch (index - aiad_paragraph)?????? ?????? source
		res.put("moreAiadDocResultSources", moreAiadDocResultSources);	// ElasticSearch (index - aiad_doc)?????? ?????? source
		
		return new ResponseEntity<>(res, HttpStatus.OK);
	}
	
	// minio client ???????????? pdf ?????????
	@RequestMapping("/getPdf")
	public ResponseEntity<?> getPdf(HttpServletRequest request, Model model, @RequestBody Map<String, Object> param) throws Exception {
		
		// MINIO api ?????? ??????
		
		String url = (String) param.get("url");
		String docTitleStr = (String) param.get("docTitleStr");
		String objectStr = url.replace(minioUrl + "/aiad", "");
		
		MinioClient minioClient =
			    MinioClient.builder()
			        .endpoint(minioUrl)
			        .credentials(minioId, minioPw)
			        .build();
		
		byte[] pdfByteArray = null;
		try {
			boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket("aiad").build());
			if (found) {
				//pdfByteArray = IOUtils.toByteArray(minioClient.getObject(GetObjectArgs.builder().bucket("aiad").object(docTitleStr).build()));
				
				// pdf object??? InputStream ??????
				InputStream stream = minioClient.getObject(GetObjectArgs.builder().bucket("aiad").object(objectStr).build());
				// InputStream??? byte ?????? ??????
				pdfByteArray = IOUtils.toByteArray(stream);
				
			}
		} catch (Exception e) {
			System.out.println("Error occurred: " + e);
		}
		
		// byte ????????? base64 ?????????
		String endcodedPdfByteArray = Base64.getEncoder().encodeToString(pdfByteArray);
		Map<String, Object> res = new HashMap<>();
		res.put("pdfByteArray", pdfByteArray);
		res.put("endcodedPdfByteArray", endcodedPdfByteArray);
		
		return new ResponseEntity<>(res, HttpStatus.OK);
	}
	
	// ?????? ??????
	@RequestMapping("/insertAnswer")
	public ResponseEntity<?> insertAnswer(HttpServletRequest request, Model model, @RequestBody Map<String, Object> param) throws Exception {
		
		String ansContent = (String) param.get("ansContent");
		String docId = (String) param.get("docId");
		String paragraphId = (String) param.get("paragraphId");
		
		AnswerVo answerVo = new AnswerVo();
		answerVo.setAnsContent(ansContent);
		answerVo.setDocId(docId);
		answerVo.setParagraphId(paragraphId);
		
		qnaService.insertAnswer(answerVo);
		answerVo = qnaService.selectAnswer(answerVo);
		
		int ansSeq = answerVo.getAnsSeq();
		
		Map<String, Object> res = new HashMap<>();
		res.put("ansSeq", ansSeq);
		
		return new ResponseEntity<>(res, HttpStatus.OK);
	}
	
	// ?????? ??????
	@RequestMapping("/selectAnswer")
	public ResponseEntity<?> selectAnswer(HttpServletRequest request, Model model, @RequestBody Map<String, Object> param) throws Exception {
		
		int ansSeq = (int) param.get("ansSeq");
		AnswerVo answerVo = new AnswerVo();
		answerVo.setAnsSeq(ansSeq);
		
		answerVo = qnaService.selectAnswer(answerVo);
		
		Map<String, Object> res = new HashMap<>();
		res.put("answer", answerVo);
		
		return new ResponseEntity<>(res, HttpStatus.OK);
	}
	
	// ?????? ??????
	@RequestMapping("/insertQuestionOpinion")
	public ResponseEntity<?> insertQuestionOpinion(HttpServletRequest request, Model model, @RequestBody Map<String, Object> param) throws Exception {
		
		HttpSession httpSession = request.getSession(true);
		String userId = (String) httpSession.getAttribute("USER_ID");
		int ansSeq = (int) param.get("ansSeq");
		String qstContent = (String) param.get("qstContent");
		String qstOpinion = (String) param.get("qstOpinion");
		int qstAnsRate = (int) param.get("qstAnsRate");
		
		QuestionVo questionVo = new QuestionVo();
		questionVo.setUserId(userId);
		questionVo.setAnsSeq(ansSeq);
		questionVo.setQstContent(qstContent);
		questionVo.setQstOpinion(qstOpinion);
		questionVo.setQstAnsRate(qstAnsRate);
		
		qnaService.insertQuestionOpinion(questionVo);
		
		return new ResponseEntity<>(HttpStatus.OK);
	}
	
}
