package com.enzobnl.annotweet.systems

import java.io.IOException

import com.enzobnl.annotweet.utils.{QuickSQLContextFactory, Utils}
import org.apache.spark.ml.{Pipeline, PipelineModel, PipelineStage}
import org.apache.spark.ml.evaluation.MulticlassClassificationEvaluator
import org.apache.spark.sql.{DataFrame, Row, SQLContext}

trait TweetSentimentAnalyzer {
  protected var _options: Map[String, Any] = Map(
    "verbose" -> true,
    "modelsPath" -> s"${this.getClass.getResource("/data").getFile}/../models",
    "dataPath" -> s"${this.getClass.getResource("/data").getFile}"
  )

  def option(key: String, value: Any): TweetSentimentAnalyzer = {
    _options += (key -> value)
    this
  }
  def options: Map[String, Any] = _options

  def getDatasetPath(datasetName: String): String = options("dataPath").asInstanceOf[String] +"/" + datasetName
  def getModelPath(modelID: String): String = options("modelsPath").asInstanceOf[String] +"/" + modelID

  protected lazy val _spark: SQLContext = QuickSQLContextFactory.getOrCreate("annotweet")
  protected var _pipelineModel: PipelineModel = null //TODO ???
  protected lazy val defaultDF: DataFrame = loadDataset("air.txt")

  def pipelineModel = _pipelineModel
  def isTrained = _pipelineModel != null

  /**
    * Save trained model
    * @param modelID
    * @return true if all went ok, else false
    */
  def save(modelID: String): Boolean = {
    try{
      this._pipelineModel.save(s"${options("modelsPath")}$modelID")
      true
    } catch {
      case _: IOException => false
      case _: NotImplementedError => false // if model not trained yet

    }
  }

  /**
    * Load a saved model (saved with trained
    * @param modelID
    * @return true if all went ok, else false
    */
  def load(modelID: String): Boolean = {
    try{
      this._pipelineModel = PipelineModel.read.load(getModelPath(modelID))
      true
    } catch{
      case _: Exception => false
    }
  }

  /**
    * Load dataset from /data folder in resources folder
    * @param datasetName
    * @return
    */
  def loadDataset(datasetName: String): DataFrame = {
    _spark.read.option("delimiter", ")").csv(getDatasetPath(datasetName)).createOrReplaceGlobalTempView("temp")
    _spark.sql("""SELECT regexp_extract(_c0, '\\(([^,]*),(.*)', 1) AS id,regexp_extract(_c0, '\\(([^,]*),(.*)', 2) AS target,_c1 AS text FROM global_temp.temp""".stripMargin)}

  /**
    * Create single line df from unlabelled row tweet text
    * @param tweet
    * @return single lined df [id, target, test]
    */
  def tweetToDF(tweet: String): DataFrame = _spark.createDataFrame(Seq(Tuple3("0", "???", tweet))).toDF("id", "target", "text")

  /**
    * Tag a single tweet text
    * @param tweet
    * @return tweet tag
    */
  def tag(tweet: String): Tag.Value = Tag.get(_pipelineModel.transform(tweetToDF(tweet)).select("unindexedLabel").collect()(0).getAs[String]("unindexedLabel"))

  /**
    * Transform a single tweet embedded in single line dataframe
    * @param tweet
    * @return transformed Row
    */
  def transformTweet(tweet: String): Row = _pipelineModel.transform(tweetToDF(tweet)).collect()(0)

//  /**
//    * CrossValidation with nChunks the numbers of chunks made out of DATA_PATH dataset
//    * @param nChunks: default is df.count() meaning that a "one out cross validation" will be performed
//    * @return (Mean, Stddev)
//    */
//  def crossValidate(nChunks: Int=defaultDF.count().toInt): TSACrossValidationResult ={
//    if(nChunks <= 1) throw new IllegalArgumentException("nChunks must be > 1")
//    // Split data in nChunks
//    val dfs: Array[DataFrame] = defaultDF.randomSplit((for (_ <- 1 to nChunks) yield 1.0).toList.toArray)
//    // List that will be feed by accuracies (size will be nChunks
//    var accuraciesList = List[Double]()
//    // Evaluator (compare label column to predictedLabel
//    val evaluator = new MulticlassClassificationEvaluator().setLabelCol("label").setPredictionCol("predictedLabel")
//    // nChunks loops
//    for(i <- 0 until nChunks){
//      if (options("verbose").asInstanceOf[Boolean]) println(s"step ${i+1}/$nChunks:::")
//      // build trainDF of size ~= (nChunks - 1)/nChunks
//      var trainDF: DataFrame = dfs.foldLeft[(Int, DataFrame)]((0, null))((i_df: (Int, DataFrame), df: DataFrame) =>
//        if (i_df._1 != i)
//          (i_df._1 + 1, if(i_df._2 != null) i_df._2.union(df) else df)
//        else (i_df._1 + 1, i_df._2))._2
//      train(trainDF)
//      // test on the i-th df in dfs of size ~= 1/nChunks and add evaluated accuracy to accuraciesList
//      accuraciesList = accuraciesList :+ evaluator.evaluate(_pipelineModel.transform(dfs(i)))
//    }
//    // return mean accuracy
//
//    val mean = accuraciesList.sum/nChunks
//    TSACrossValidationResult(mean, Math.sqrt(accuraciesList.foldLeft[Double](0)((acc: Double, t: Double) => acc + Math.pow(t - mean, 2))/nChunks))
//  }

  /**
    * Load modelID model, else train it on trainDF
    * @param modelID
    * @param trainDF
    * @return time elapsed for train or 0 if model loaded
    */
  def loadOrTrain(modelID: String, trainDF: DataFrame=defaultDF): Double = {
    if(!this.load(modelID)) this.train(trainDF) else 0
  }

  //ABSTRACT:

  /**
    * Train system.
    * @param trainDF: default: entire dataFrame
    * @return time elapsed
    */
  def train(trainDF: DataFrame=defaultDF): Double
}
