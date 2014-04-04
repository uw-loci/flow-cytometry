%ImageHandler that should be registered to the PrairieLink ImageObject's
%"ImageUpdated" event by using: 
%pl.registerevent({'ImageUpdated' 'NewPVImageHandler'})
function NewPVImageHandler( varargin )

minParticleSize = 1000;
maxParticleSize = 2300;
gaussianSigma = 2.2;
thresholdMin = 30;
ratioUpperLimit = 0.9;
ratioLowerLimit = 0.001;

captures = 0;
bool_capture = false;

 brightfield = varargin{end -1}.Source.getImage(1);
 intensity = varargin{end -1}.Source.getImage(2);

    %Reshapes the buffer into an image that is pixelsPerLine x
    %linesPerFrame
    brightfield = reshape(brightfield(1:varargin{end -1}.Source.pixelsPerLine* ...
        varargin{end -1}.Source.linesPerFrame),varargin{end -1}.Source.pixelsPerLine,...
        varargin{end -1}.Source.linesPerFrame);
    brightfield = brightfield';
    
    %Reshapes the buffer into an image that is pixelsPerLine x
    %linesPerFrame
    intensity = reshape(intensity(1:varargin{end -1}.Source.pixelsPerLine* ...
        varargin{end -1}.Source.linesPerFrame),varargin{end -1}.Source.pixelsPerLine,...
        varargin{end -1}.Source.linesPerFrame);
    intensity = intensity';
    
%     figure(1)
%     hold on
%     subplot(1,2,1)
%     imshow(brightfield,[]);
%     subplot(1,2,2)
%     imshow(intensity,[]);
%     hold off
    
    %create brightfield mask 
    [ brightfield_mask, particlesPixelCount ] = createBFMask( uint8(brightfield), minParticleSize, maxParticleSize, gaussianSigma);
    %imshow(brightfield_mask)

    %threshold intensity image
    intensity_image = uint8(intensity) > thresholdMin;

    %multiply mask with intensity image to find intensity INSIDE particles
    intensity_masked = immultiply(brightfield_mask, intensity_image);

    %(find ratios)
    %compute ratio
    ratioValues = computeRatio( brightfield_mask, intensity_masked );

    %gate particle
    if(~isempty(ratioValues))
        for index = 1:size(ratioValues)
            if (ratioValues(index) < ratioUpperLimit) && (ratioValues(index) > ratioLowerLimit)
                captures = captures+1;
                bool_capture = true;    %this is a temporary action to be replaced with hardware control code
                break;
            end
        end
    end

    
    
    figure(8)
    subplot(2,2,1)
    imshow(brightfield,[]);
    subplot(2,2,2)
    imshow(brightfield_mask,[]);
    subplot(2,2,3)
    imshow(intensity,[]);
    subplot(2,2,4)
    imshow(intensity_masked,[]);


% 
% %disp('COM Event handled - New Buffer')
% 
%  %Get the image from channel 1
%  %Change to appropriate channel if necessary
%  buff = varargin{end -1}.Source.getImage(1);
% 
%     %Reshapes the buffer into an image that is pixelsPerLine x
%     %linesPerFrame
%     y = reshape(buff(1:varargin{end -1}.Source.pixelsPerLine* ...
%         varargin{end -1}.Source.linesPerFrame),varargin{end -1}.Source.pixelsPerLine,...
%         varargin{end -1}.Source.linesPerFrame);
%     y = y';
%     
%     %Displays the figure in figure1
%     figure(1)
%        y = edge(double(y),'canny'); % Performs an edge filter on the image
%     imshow(y,[]);
end